package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioCompositionDto;
import ch.allianz.youngoitv.jt.dto.WeightedSymbolDto;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompareServiceImplTest {

    @Mock
    private PriceLookupService priceLookupService;

    @Test
    void assetClassWithMissingHistoryIsExcludedInsteadOfFailing() {
        CompareServiceImpl service = new CompareServiceImpl(priceLookupService);
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("SPY"), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(1);
                    return date.isBefore(LocalDate.now().minusMonths(1).withDayOfMonth(1))
                            ? Optional.<BigDecimal>empty()
                            : Optional.of(date.getMonthValue() == LocalDate.now().minusMonths(1).getMonthValue()
                                    ? new BigDecimal("100") : new BigDecimal("110"));
                });
        lenient().when(priceLookupService.findPriceAtOrBefore(
                org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.equals("SPY")), any()))
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
        CompareServiceImpl service = new CompareServiceImpl(priceLookupService);
        LocalDate firstMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return Optional.of(date.isBefore(firstMonth.plusMonths(1)) ? new BigDecimal("50") : new BigDecimal("100"));
        });
        when(priceLookupService.findPriceAtOrBefore(eq("BBB"), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return Optional.of(date.isBefore(firstMonth.plusMonths(1)) ? new BigDecimal("200") : new BigDecimal("180"));
        });

        var portfolioA = new PortfolioCompositionDto("Nur AAA", List.of(new WeightedSymbolDto("AAA", BigDecimal.ONE)));
        var portfolioB = new PortfolioCompositionDto("Nur BBB", List.of(new WeightedSymbolDto("BBB", BigDecimal.ONE)));
        var request = new ComparePortfoliosRequestDto(portfolioA, portfolioB, 1);

        var result = service.comparePortfolios(request);

        assertThat(result.series()).isNotEmpty();
        var lastPoint = result.series().get(result.series().size() - 1);
        // AAA verdoppelt sich (50->100): normalisiert 100 -> 200.
        assertThat(lastPoint.portfolioAValue()).isEqualByComparingTo("200.00");
        // BBB faellt um 10% (200->180): normalisiert 100 -> 90.
        assertThat(lastPoint.portfolioBValue()).isEqualByComparingTo("90.00");
    }

    @Test
    void comparePortfoliosExcludesDatesWhereAPositionHasNoPrice() {
        CompareServiceImpl service = new CompareServiceImpl(priceLookupService);
        when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any())).thenReturn(Optional.of(new BigDecimal("50")));
        when(priceLookupService.findPriceAtOrBefore(eq("CCC"), any())).thenReturn(Optional.empty());

        var portfolioA = new PortfolioCompositionDto("Mix", List.of(
                new WeightedSymbolDto("AAA", BigDecimal.ONE), new WeightedSymbolDto("CCC", BigDecimal.ONE)));
        var portfolioB = new PortfolioCompositionDto("Nur AAA", List.of(new WeightedSymbolDto("AAA", BigDecimal.ONE)));
        var request = new ComparePortfoliosRequestDto(portfolioA, portfolioB, 1);

        var result = service.comparePortfolios(request);

        assertThat(result.series()).isNotEmpty();
        assertThat(result.series()).allSatisfy(point -> assertThat(point.portfolioAValue()).isNull());
        assertThat(result.series()).allSatisfy(point -> assertThat(point.portfolioBValue()).isEqualByComparingTo("100.00"));
    }

    @Test
    void zeroTotalWeightYieldsNoValueInsteadOfDivisionByZero() {
        CompareServiceImpl service = new CompareServiceImpl(priceLookupService);
        var portfolioA = new PortfolioCompositionDto("Leer", List.of(new WeightedSymbolDto("AAA", BigDecimal.ZERO)));
        var portfolioB = new PortfolioCompositionDto("Nur AAA", List.of(new WeightedSymbolDto("AAA", BigDecimal.ONE)));
        lenient().when(priceLookupService.findPriceAtOrBefore(eq("AAA"), any())).thenReturn(Optional.of(new BigDecimal("50")));
        var request = new ComparePortfoliosRequestDto(portfolioA, portfolioB, 1);

        var result = service.comparePortfolios(request);

        assertThat(result.series()).allSatisfy(point -> assertThat(point.portfolioAValue()).isNull());
    }
}
