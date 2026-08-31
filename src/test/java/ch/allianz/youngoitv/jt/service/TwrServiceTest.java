package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TwrServiceTest {

    private final TwrService twrService = new TwrService();

    /**
     * Handrechnung (unabhängig von der Implementierung):
     * Periode 1: (1100 - 1000 - 0) / 1000   = 0.10
     * Periode 2: (1400 - 1100 - 200) / 1100 = 100 / 1100 = 0.0909090909...
     * TWR = 1.10 * 1.0909090909... - 1 = 0.20
     *
     * <p>Der Zufluss von 200 bleibt aus dem Nenner: gemessen wird die Rendite auf dem Kapital, das zu
     * Periodenbeginn schon da war. Von 1100 auf 1400 bei 200 neuem Geld sind 100 verdient, und zwar auf
     * 1100 - also dieselben rund 9 %, die auch ein Depot ohne Zufluss von 1100 auf 1200 hätte.</p>
     */
    @Test
    void chainsTwoSubPeriodReturnsCorrectly() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1100")),
                new ValuationPeriod(new BigDecimal("1100"), new BigDecimal("200"), new BigDecimal("1400")));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result.doubleValue()).isCloseTo(0.20, org.assertj.core.data.Offset.offset(1e-8));
    }

    /**
     * Der Verkauf des halben Depots darf die Tagesrendite nicht verzerren: 1000 zu Beginn, 600 verkauft,
     * 420 am Ende - verdient sind 20 auf 1000, also 2 %. Mit dem Abfluss im Nenner (1000-600=400) wären
     * es 5 %, und je vollständiger der Verkauf, desto grösser der Fehler.
     */
    @Test
    void aLargeOutflowDoesNotInflateThePeriodReturn() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), new BigDecimal("-600"), new BigDecimal("420")));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.0200000000"));
    }

    /**
     * Vollverkauf: 1000 zu Beginn, alles für 1100 verkauft, am Ende liegt nichts mehr im Depot. Das sind
     * 10 % und nicht "keine Messung möglich" - mit dem Abfluss im Nenner wäre der negativ gewesen.
     */
    @Test
    void aCompleteLiquidationStillMeasuresTheDaysReturn() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), new BigDecimal("-1100"), BigDecimal.ZERO));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.1000000000"));
    }

    /**
     * Die erste Anlage überhaupt: vorher kein Kapital, 1000 investiert, am Abend 1050 wert. Hier ist der
     * Zufluss die Bezugsgrösse, sonst wäre der Nenner 0 und der Tag verloren.
     */
    @Test
    void theFirstInvestmentIsMeasuredAgainstTheInflow() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(BigDecimal.ZERO, new BigDecimal("1000"), new BigDecimal("1050")));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.0500000000"));
    }

    @Test
    void noPeriodsResultInZeroReturn() {
        BigDecimal result = twrService.calculate(List.of());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aLossPeriodProducesANegativeReturn() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("900")));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result).isEqualByComparingTo(new BigDecimal("-0.1000000000"));
    }

    /** Dieselbe Handrechnung wie oben, nur Schritt für Schritt: 1.10 und dann 1.10 * 1.090909... */
    @Test
    void theChainYieldsTheCumulativeFactorAfterEachPeriod() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1100")),
                new ValuationPeriod(new BigDecimal("1100"), new BigDecimal("200"), new BigDecimal("1400")));

        List<BigDecimal> factors = twrService.chain(periods);

        assertThat(factors).hasSize(2);
        assertThat(factors.get(0).doubleValue()).isCloseTo(1.10, org.assertj.core.data.Offset.offset(1e-8));
        assertThat(factors.get(1).doubleValue()).isCloseTo(1.20, org.assertj.core.data.Offset.offset(1e-8));
    }

    /**
     * Der Endwert der Kette und die Kennzahl müssen dieselbe Zahl sein - sonst zeigt das Diagramm eine
     * andere Entwicklung als die Kachel daneben.
     */
    @Test
    void theLastChainFactorAgreesWithTheHeadlineReturn() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1100")),
                new ValuationPeriod(new BigDecimal("1100"), new BigDecimal("200"), new BigDecimal("1400")),
                new ValuationPeriod(new BigDecimal("1400"), new BigDecimal("-100"), new BigDecimal("1250")));

        List<BigDecimal> factors = twrService.chain(periods);

        assertThat(factors.get(factors.size() - 1).subtract(BigDecimal.ONE))
                .isEqualByComparingTo(twrService.calculate(periods));
    }

    @Test
    void noPeriodsYieldAnEmptyChain() {
        assertThat(twrService.chain(List.of())).isEmpty();
    }

    /**
     * Eine Periode ohne Kapital zu Beginn zählt als 0 %: der Faktor bleibt stehen, statt die Kette mit
     * einer Division durch 0 zu beenden. Danach wird ganz normal weitergerechnet.
     */
    @Test
    void aPeriodWithoutCapitalLeavesTheChainUnchanged() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1200")));

        List<BigDecimal> factors = twrService.chain(periods);

        assertThat(factors.get(0)).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(factors.get(1).doubleValue()).isCloseTo(1.20, org.assertj.core.data.Offset.offset(1e-8));
    }
}
