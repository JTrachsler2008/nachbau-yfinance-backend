package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.dto.SparplanRequestDto;
import ch.allianz.youngoitv.jt.service.RebalancingMode;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SparplanSimulationServiceImplTest {

    @Mock
    private PriceLookupService priceLookupService;

    @Test
    void constantPriceAccumulatesSharesEachMonth() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("SPY"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(1).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("SPY", new BigDecimal("100")), false, 12, RebalancingMode.INTERVAL, BigDecimal.TEN);

        var result = service.simulate(request);

        assertThat(result.chartData()).hasSize(2);
        assertThat(result.invested()).isEqualByComparingTo("2000.00");
        // 1000/100=10 Anteile pro Monat, 2 Monate => 20 Anteile * 100 = 2000.
        assertThat(result.endValue()).isEqualByComparingTo("2000.00");
        assertThat(result.gain()).isEqualByComparingTo("0.00");
    }

    @Test
    void intervalRebalancingTradesSumToZero() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("BBB"), any()))
                .thenReturn(Optional.of(new BigDecimal("50")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(3).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("AAA", new BigDecimal("70"), "BBB", new BigDecimal("30")),
                true, 1, RebalancingMode.INTERVAL, BigDecimal.TEN);

        var result = service.simulate(request);

        assertThat(result.rebalancingEvents()).isNotEmpty();
        for (var event : result.rebalancingEvents()) {
            BigDecimal sumOfTrades = event.trades().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumOfTrades).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void tradesSumToZeroEvenWhenInputWeightsDoNotAddUpTo100() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("BBB"), any()))
                .thenReturn(Optional.of(new BigDecimal("50")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        // Gewichte summieren sich bewusst auf 90 statt 100 - normalizeToFractionsSummingToOne muss
        // durch die tatsächliche Summe (90) teilen, nicht durch eine hartkodierte 100.
        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(3).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("AAA", new BigDecimal("60"), "BBB", new BigDecimal("30")),
                true, 1, RebalancingMode.INTERVAL, BigDecimal.TEN);

        var result = service.simulate(request);

        assertThat(result.rebalancingEvents()).isNotEmpty();
        for (var event : result.rebalancingEvents()) {
            BigDecimal sumOfTrades = event.trades().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumOfTrades).isEqualByComparingTo(BigDecimal.ZERO);
        }
        // 60/(60+30)=2/3 der Zielallokation, nicht 60%.
        assertThat(result.targetAllocationPercent().get("AAA")).isEqualByComparingTo("66.67");
    }

    @Test
    void thresholdRebalancingTriggersWhenAllocationDriftsBeyondBand() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(1);
                    // AAA verdoppelt sich nach dem ersten Monat -> Allokation driftet klar über jedes Band.
                    boolean firstMonth = date.isBefore(LocalDate.now().minusMonths(2).withDayOfMonth(1).plusMonths(1));
                    return Optional.of(firstMonth ? new BigDecimal("100") : new BigDecimal("200"));
                });
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("BBB"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(2).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("AAA", new BigDecimal("50"), "BBB", new BigDecimal("50")),
                true, 12, RebalancingMode.THRESHOLD, new BigDecimal("5"));

        var result = service.simulate(request);

        assertThat(result.rebalancingEvents()).isNotEmpty();
        assertThat(result.rebalancingEvents()).allSatisfy(event -> assertThat(event.reason()).isEqualTo("schwelle"));
        for (var event : result.rebalancingEvents()) {
            BigDecimal sumOfTrades = event.trades().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumOfTrades).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void thresholdRebalancingDoesNotTriggerWhenAllocationStaysWithinBand() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("BBB"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(2).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("AAA", new BigDecimal("50"), "BBB", new BigDecimal("50")),
                true, 12, RebalancingMode.THRESHOLD, new BigDecimal("5"));

        var result = service.simulate(request);

        assertThat(result.rebalancingEvents()).isEmpty();
    }

    @Test
    void zeroIntervalMonthsThrowsInsteadOfDivisionByZero() {
        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(1).withDayOfMonth(1), new BigDecimal("1000"), 0,
                Map.of("AAA", new BigDecimal("100")), false, 12, RebalancingMode.INTERVAL, BigDecimal.TEN);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> new SparplanSimulationServiceImpl(priceLookupService).simulate(request)))
                .isInstanceOf(ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException.class);
    }

    @Test
    void singleMonthSparplanComputesEndValueWithoutCagrDivisionByZero() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("SPY"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("SPY", new BigDecimal("100")), false, 12, RebalancingMode.INTERVAL, BigDecimal.TEN);

        var result = service.simulate(request);

        assertThat(result.chartData()).hasSize(1);
        assertThat(result.invested()).isEqualByComparingTo("1000.00");
        assertThat(result.endValue()).isEqualByComparingTo("1000.00");
        // years=0 zwischen Start und Ende desselben Monats -> CAGR bewusst 0 statt Division durch 0.
        assertThat(result.cagrPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void missingPriceForAPositionIsSkippedInsteadOfFailing() {
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("ZZZ"), any()))
                .thenReturn(Optional.empty());
        SparplanSimulationServiceImpl service = new SparplanSimulationServiceImpl(priceLookupService);

        var request = new SparplanRequestDto(
                LocalDate.now().minusMonths(1).withDayOfMonth(1), new BigDecimal("1000"), 1,
                Map.of("AAA", new BigDecimal("50"), "ZZZ", new BigDecimal("50")),
                false, 12, RebalancingMode.INTERVAL, BigDecimal.TEN);

        var result = service.simulate(request);

        assertThat(result.chartData()).isNotEmpty();
        // ZZZ hat nie einen Preis -> nur AAA-Anteil zählt zum Wert.
        assertThat(result.endValue()).isGreaterThan(BigDecimal.ZERO);
    }
}
