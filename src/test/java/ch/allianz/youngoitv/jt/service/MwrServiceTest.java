package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MwrServiceTest {

    private final MwrService mwrService = new MwrService();

    /**
     * Klassisches, von Hand nachvollziehbares Beispiel: Investition von 1000 am Tag 0, Rückfluss
     * 1200 nach genau einem Jahr. -1000 + 1200/(1+r)^1 = 0  =>  1+r = 1.2  =>  r = 0.20 (20%).
     */
    @Test
    void singleCashFlowPairResolvesToTheExactAnalyticalRate() {
        List<CashFlow> cashFlows = List.of(
                new CashFlow(LocalDate.of(2026, 1, 1), new BigDecimal("-1000")),
                new CashFlow(LocalDate.of(2027, 1, 1), new BigDecimal("1200")));

        BigDecimal result = mwrService.calculate(cashFlows);

        assertThat(result.doubleValue()).isCloseTo(0.20, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void breakEvenInvestmentResolvesToApproximatelyZero() {
        List<CashFlow> cashFlows = List.of(
                new CashFlow(LocalDate.of(2026, 1, 1), new BigDecimal("-1000")),
                new CashFlow(LocalDate.of(2027, 1, 1), new BigDecimal("1000")));

        BigDecimal result = mwrService.calculate(cashFlows);

        assertThat(result.doubleValue()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void emptyCashFlowListReturnsZeroInsteadOfThrowing() {
        BigDecimal result = mwrService.calculate(List.of());

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
