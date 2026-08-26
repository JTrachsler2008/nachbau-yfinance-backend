package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class RiskServiceTest {

    private final RiskService riskService = new RiskService();

    private static List<BigDecimal> decimalsOf(double... values) {
        List<BigDecimal> list = new java.util.ArrayList<>();
        for (double v : values) {
            list.add(BigDecimal.valueOf(v));
        }
        return list;
    }

    /**
     * Handrechnung: Renditen [0.02,-0.02,0.02,-0.02], Mittelwert 0, Varianz = Mittel der Quadrate =
     * 0.0004, Tages-Stddev = 0.02. Annualisiert: 0.02 * sqrt(252) = 0.02 * 15.8745... = 0.31749...
     */
    @Test
    void annualizedVolatilityMatchesHandComputedValue() {
        List<BigDecimal> returns = decimalsOf(0.02, -0.02, 0.02, -0.02);

        BigDecimal result = riskService.annualizedVolatility(returns);

        assertThat(result.doubleValue()).isCloseTo(0.02 * Math.sqrt(252), Offset.offset(1e-9));
    }

    /**
     * Handrechnung: portfolioReturns ist exakt das Doppelte von benchmarkReturns bei jedem Punkt =>
     * Kovarianz/Varianz(Benchmark) = 2.0 exakt, unabhängig von den konkreten Werten.
     */
    @Test
    void betaOfExactlyDoubledReturnsIsExactlyTwo() {
        List<BigDecimal> benchmark = decimalsOf(0.01, -0.01, 0.02, -0.02);
        List<BigDecimal> portfolio = decimalsOf(0.02, -0.02, 0.04, -0.04);

        BigDecimal result = riskService.beta(portfolio, benchmark);

        assertThat(result.doubleValue()).isCloseTo(2.0, Offset.offset(1e-9));
    }

    @Test
    void betaWithZeroBenchmarkVarianceThrows() {
        List<BigDecimal> benchmark = decimalsOf(0.01, 0.01, 0.01);
        List<BigDecimal> portfolio = decimalsOf(0.02, -0.02, 0.04);

        assertThatThrownBy(() -> riskService.beta(portfolio, benchmark))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Handrechnung: Werte [100,120,90,130,80]. Laufender Peak: 100,120,120,130,130.
     * Drawdowns: 0, 0, (90-120)/120=-0.25, 0, (80-130)/130=-0.384615...
     * Grösster (negativster) Drawdown = -0.384615...
     */
    @Test
    void maxDrawdownMatchesHandComputedValue() {
        List<BigDecimal> values = decimalsOf(100, 120, 90, 130, 80);

        BigDecimal result = riskService.maxDrawdown(values);

        assertThat(result.doubleValue()).isCloseTo(-0.3846153846, Offset.offset(1e-8));
    }

    /**
     * Dieselbe Reihe wie {@code maxDrawdownMatchesHandComputedValue}: der grösste Rückgang ist
     * (80-130)/130 = -38.46...%, vom bis dahin letzten Höchststand bei Index 3 (Wert 130) zum
     * Tiefpunkt bei Index 4 (Wert 80) - nicht vom früheren, kleineren Hoch bei Index 1 (120), dessen
     * eigener Rückgang auf Index 2 mit -25% kleiner ausfällt.
     */
    @Test
    void maxDrawdownPeriodFindsThePeakAndTroughPositions() {
        List<BigDecimal> values = decimalsOf(100, 120, 90, 130, 80);

        DrawdownPeriod result = riskService.maxDrawdownPeriod(values);

        assertThat(result.peakIndex()).isEqualTo(3);
        assertThat(result.troughIndex()).isEqualTo(4);
        assertThat(result.drawdown().doubleValue()).isCloseTo(-0.3846153846, Offset.offset(1e-8));
    }

    @Test
    void maxDrawdownPeriodOfMonotonicallyRisingSeriesPointsAtTheStart() {
        List<BigDecimal> values = decimalsOf(100, 110, 120, 130);

        DrawdownPeriod result = riskService.maxDrawdownPeriod(values);

        assertThat(result.peakIndex()).isEqualTo(0);
        assertThat(result.troughIndex()).isEqualTo(0);
        assertThat(result.drawdown()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void maxDrawdownOfMonotonicallyRisingSeriesIsZero() {
        List<BigDecimal> values = decimalsOf(100, 110, 120, 130);

        BigDecimal result = riskService.maxDrawdown(values);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Handrechnung: 10 sortierte Renditen, 5%-Perzentil (Nearest-Rank) bei n=10 => rank = ceil(0.5) = 1
     * => Index 0 => der schlechteste (kleinste) Wert der Reihe.
     */
    @Test
    void valueAtRisk95ReturnsTheWorstReturnForTenObservations() {
        List<BigDecimal> returns = decimalsOf(-0.05, -0.03, -0.01, 0.00, 0.01, 0.01, 0.02, 0.02, 0.03, 0.04);

        BigDecimal result = riskService.valueAtRisk95(returns);

        assertThat(result).isEqualByComparingTo(new BigDecimal("-0.05"));
    }

    /**
     * Handrechnung: +10% und danach -10% ergeben 1.1 * 0.9 = 0.99, also einen Verlust von 1% in zwei
     * Handelstagen. Hochgerechnet auf ein Jahr: 0.99^(252/2) = 0.99^126 = 0.28186, somit -71.81%.
     * Eine arithmetische Annualisierung würde hier 0% liefern, weil sich +0.10 und -0.10 aufheben.
     */
    @Test
    void annualizedReturnChainsTheReturnsInsteadOfAddingThem() {
        List<BigDecimal> returns = decimalsOf(0.10, -0.10);

        BigDecimal result = riskService.annualizedReturn(returns);

        assertThat(result.doubleValue()).isCloseTo(-0.7181, Offset.offset(1e-4));
    }

    /**
     * Handrechnung: 252 Tage mit je +0.1% sind genau ein Jahr, die Hochrechnung ist also der Zeitraum
     * selbst: 1.001^252 = 1.28644, somit +28.64%.
     */
    @Test
    void annualizedReturnOverExactlyOneYearIsTheChainedReturnItself() {
        List<BigDecimal> returns = new java.util.ArrayList<>(java.util.Collections.nCopies(252, new BigDecimal("0.001")));

        BigDecimal result = riskService.annualizedReturn(returns);

        assertThat(result.doubleValue()).isCloseTo(0.28644, Offset.offset(1e-5));
    }

    @Test
    void annualizedReturnOfATotalLossIsMinusOneHundredPercent() {
        // Ein Tag mit -100% löscht das Kapital. Ohne Sonderfall stünde hier eine Wurzel aus 0 bzw.
        // aus einer negativen Zahl, und ein weiterer Gewinntag danach könnte den Verlust rechnerisch
        // wieder aufheben, was fachlich unmöglich ist.
        List<BigDecimal> returns = decimalsOf(-1.0, 0.05);

        BigDecimal result = riskService.annualizedReturn(returns);

        assertThat(result).isEqualByComparingTo(new BigDecimal("-1.0"));
    }

    @Test
    void sharpeRatioIsZeroWhenReturnEqualsRiskFreeRateExactly() {
        // Konstante Tagesrendite, deren annualisierter Wert exakt dem risikofreien Zins entspricht,
        // aber Volatilität 0 -> Sonderfall, der explizit 0 statt einer Division durch 0 liefern muss.
        List<BigDecimal> returns = decimalsOf(0.0, 0.0, 0.0, 0.0);

        BigDecimal result = riskService.sharpeRatio(returns);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
