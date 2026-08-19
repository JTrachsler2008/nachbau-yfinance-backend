package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioCompositionDto;
import ch.allianz.youngoitv.jt.dto.WeightedSymbolDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompareServiceImplTest {

    @Mock
    private MarketDataProvider marketDataProvider;

    @Test
    void assetClassWithMissingHistoryIsExcludedInsteadOfFailing() {
        CompareServiceImpl service = new CompareServiceImpl(marketDataProvider);
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.eq("SPY"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.of(List.of(
                        new HistoricalPrice(LocalDate.of(2024, 1, 1), new BigDecimal("100")),
                        new HistoricalPrice(LocalDate.of(2024, 2, 1), new BigDecimal("110")))));
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.argThat(s -> s != null && !s.equals("SPY")),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.empty());

        AssetClassComparisonResponseDto result = service.getAssetClassComparison(1);

        assertThat(result.assetClasses()).hasSize(1);
        assertThat(result.assetClasses().get(0).symbol()).isEqualTo("SPY");
        assertThat(result.series()).hasSize(2);
        assertThat(result.series().get(0).valuesBySymbol().get("SPY")).isEqualByComparingTo("100.00");
        assertThat(result.series().get(1).valuesBySymbol().get("SPY")).isEqualByComparingTo("110.00");
    }

    @Test
    void comparePortfoliosNormalizesBothPortfoliosToTheSameBase() {
        CompareServiceImpl service = new CompareServiceImpl(marketDataProvider);
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.eq("AAA"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.of(List.of(
                        new HistoricalPrice(LocalDate.of(2024, 1, 1), new BigDecimal("50")),
                        new HistoricalPrice(LocalDate.of(2024, 2, 1), new BigDecimal("100")))));
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.eq("BBB"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.of(List.of(
                        new HistoricalPrice(LocalDate.of(2024, 1, 1), new BigDecimal("200")),
                        new HistoricalPrice(LocalDate.of(2024, 2, 1), new BigDecimal("180")))));

        var portfolioA = new PortfolioCompositionDto("Nur AAA", List.of(new WeightedSymbolDto("AAA", BigDecimal.ONE)));
        var portfolioB = new PortfolioCompositionDto("Nur BBB", List.of(new WeightedSymbolDto("BBB", BigDecimal.ONE)));
        var request = new ComparePortfoliosRequestDto(portfolioA, portfolioB, 1);

        var result = service.comparePortfolios(request);

        assertThat(result.series()).hasSize(2);
        // AAA verdoppelt sich (50->100): normalisiert 100 -> 200.
        assertThat(result.series().get(1).portfolioAValue()).isEqualByComparingTo("200.00");
        // BBB faellt um 10% (200->180): normalisiert 100 -> 90.
        assertThat(result.series().get(1).portfolioBValue()).isEqualByComparingTo("90.00");
    }

    @Test
    void comparePortfoliosExcludesDatesWhereAPositionHasNoPrice() {
        CompareServiceImpl service = new CompareServiceImpl(marketDataProvider);
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.eq("AAA"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2024, 1, 1), new BigDecimal("50")))));
        when(marketDataProvider.getHistorical(
                ArgumentMatchers.eq("CCC"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(Interval.MONTHLY)))
                .thenReturn(Optional.empty());

        var portfolioA = new PortfolioCompositionDto("Mix", List.of(
                new WeightedSymbolDto("AAA", BigDecimal.ONE), new WeightedSymbolDto("CCC", BigDecimal.ONE)));
        var portfolioB = new PortfolioCompositionDto("Nur AAA", List.of(new WeightedSymbolDto("AAA", BigDecimal.ONE)));
        var request = new ComparePortfoliosRequestDto(portfolioA, portfolioB, 1);

        var result = service.comparePortfolios(request);

        assertThat(result.series()).hasSize(1);
        assertThat(result.series().get(0).portfolioAValue()).isNull();
        assertThat(result.series().get(0).portfolioBValue()).isEqualByComparingTo("100.00");
    }
}
