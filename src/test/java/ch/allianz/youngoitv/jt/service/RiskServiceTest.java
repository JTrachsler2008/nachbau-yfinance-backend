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
     * Kovarianz/Varianz(Benchmark) = 2.0 exakt, unabhaengig von den konkreten Werten.
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
     * Groesster (negativster) Drawdown = -0.384615...
     */
    @Test
    void maxDrawdownMatchesHandComputedValue() {
        List<BigDecimal> values = decimalsOf(100, 120, 90, 130, 80);

        BigDecimal result = riskService.maxDrawdown(values);

        assertThat(result.doubleValue()).isCloseTo(-0.3846153846, Offset.offset(1e-8));
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

    @Test
    void sharpeRatioIsZeroWhenReturnEqualsRiskFreeRateExactly() {
        // Konstante Tagesrendite, deren annualisierter Wert exakt dem risikofreien Zins entspricht,
        // aber Volatilitaet 0 -> Sonderfall, der explizit 0 statt einer Division durch 0 liefern muss.
        List<BigDecimal> returns = decimalsOf(0.0, 0.0, 0.0, 0.0);

        BigDecimal result = riskService.sharpeRatio(returns);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
