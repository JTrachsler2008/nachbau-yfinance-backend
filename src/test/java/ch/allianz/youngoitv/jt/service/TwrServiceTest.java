package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TwrServiceTest {

    private final TwrService twrService = new TwrService();

    /**
     * Handrechnung (unabhängig von der Implementierung):
     * Periode 1: (1100 - 1000 - 0) / 1000        = 0.10
     * Periode 2: (1400 - 1100 - 200) / (1100+200) = 100 / 1300 = 0.0769230769...
     * TWR = 1.10 * 1.0769230769... - 1 = 0.1846153846...
     */
    @Test
    void chainsTwoSubPeriodReturnsCorrectly() {
        List<ValuationPeriod> periods = List.of(
                new ValuationPeriod(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1100")),
                new ValuationPeriod(new BigDecimal("1100"), new BigDecimal("200"), new BigDecimal("1400")));

        BigDecimal result = twrService.calculate(periods);

        assertThat(result.doubleValue()).isCloseTo(0.1846153846, org.assertj.core.data.Offset.offset(1e-8));
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
}
