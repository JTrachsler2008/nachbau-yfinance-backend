package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.dto.WeightItemDto;
import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
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
class SimulationServiceImplTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private FxConversionService fxConversionService;

    @Mock
    private PriceLookupService priceLookupService;

    private Portfolio portfolio(String baseCurrency) {
        Portfolio portfolio = new Portfolio();
        portfolio.setId(1L);
        portfolio.setBaseCurrency(baseCurrency);
        return portfolio;
    }

    private Position position(String symbol, String tradingCurrency, BigDecimal quantity) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setTradingCurrency(tradingCurrency);
        Position position = new Position();
        position.setSecurity(security);
        position.setTotalQuantity(quantity);
        position.setAccount(new Account());
        return position;
    }

    @Test
    void simulatePurchaseAddsNewPositionAtCorrectWeight() {
        when(portfolioService.getOwnedOrThrow(1L, "erik")).thenReturn(portfolio("CHF"));
        when(positionRepository.findByAccountPortfolioId(1L)).thenReturn(
                List.of(position("EXISTING", "CHF", new BigDecimal("10"))));
        when(marketDataProvider.getQuote("EXISTING")).thenReturn(Optional.of(new Quote("EXISTING", new BigDecimal("100"), "CHF", null)));
        lenient().when(fxConversionService.convert(any(), eq("CHF"), eq("CHF"), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(marketDataProvider.getQuote("NEW")).thenReturn(Optional.of(new Quote("NEW", new BigDecimal("50"), "CHF", null)));
        when(marketDataProvider.getInfo("NEW")).thenReturn(Optional.empty());

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService)
                .simulatePurchase(1L, "erik", "new", new BigDecimal("10"));

        // Bestand: 10*100=1000 CHF. Kauf: 10*50=500 CHF. Simuliert gesamt: 1500 CHF.
        assertThat(result.currentPortfolioValue()).isEqualByComparingTo("1000.00");
        assertThat(result.cost()).isEqualByComparingTo("500.00");
        assertThat(result.simulatedPortfolioValue()).isEqualByComparingTo("1500.00");
        // Über extracting und nicht über anySatisfy mit einem if darin: eine Bedingung im Lambda gilt
        // für jedes andere Element als erfüllt, die Zusage wäre also auch ohne NEW grün gewesen.
        assertThat(result.simulatedWeights())
                .extracting(WeightItemDto::symbol, WeightItemDto::percent)
                .containsExactly(
                        tuple("EXISTING", new BigDecimal("66.67")),
                        tuple("NEW", new BigDecimal("33.33")));
    }

    /**
     * Ein Zukauf in ein Symbol, das schon im Depot liegt, erhöht dessen Gewicht.
     *
     * Vorher standen Altbestand und Zukauf als zwei Einträge mit demselben Symbol in der Antwort. Der
     * Ring "nachher" zeichnete daraufhin zwei Segmente mit derselben Beschriftung, und der Vergleich
     * vorher/nachher in der Oberfläche behielt je Symbol nur einen der beiden Werte - für einen Kauf
     * meldete er dann eine gesunkene Gewichtung.
     */
    @Test
    void simulatePurchaseOfHeldSymbolRaisesItsWeightInsteadOfSplittingIt() {
        when(portfolioService.getOwnedOrThrow(1L, "gioia")).thenReturn(portfolio("CHF"));
        when(positionRepository.findByAccountPortfolioId(1L)).thenReturn(List.of(
                position("AAPL", "CHF", new BigDecimal("10")),
                position("NESN", "CHF", new BigDecimal("10"))));
        when(marketDataProvider.getQuote("AAPL")).thenReturn(Optional.of(new Quote("AAPL", new BigDecimal("100"), "CHF", null)));
        when(marketDataProvider.getQuote("NESN")).thenReturn(Optional.of(new Quote("NESN", new BigDecimal("50"), "CHF", null)));
        when(marketDataProvider.getInfo("AAPL")).thenReturn(Optional.empty());
        lenient().when(fxConversionService.convert(any(), eq("CHF"), eq("CHF"), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService)
                .simulatePurchase(1L, "gioia", "aapl", new BigDecimal("5"));

        // Bestand: AAPL 1000, NESN 500, gesamt 1500. Zukauf 5*100=500, simuliert gesamt 2000.
        assertThat(result.currentWeights())
                .extracting(WeightItemDto::symbol, WeightItemDto::value)
                .containsExactly(
                        tuple("AAPL", new BigDecimal("1000")),
                        tuple("NESN", new BigDecimal("500")));
        // Ein Eintrag je Symbol, und AAPL trägt Altbestand plus Zukauf: 1500 von 2000 sind 75 Prozent.
        assertThat(result.simulatedWeights())
                .extracting(WeightItemDto::symbol, WeightItemDto::value, WeightItemDto::percent)
                .containsExactly(
                        tuple("AAPL", new BigDecimal("1500"), new BigDecimal("75.00")),
                        tuple("NESN", new BigDecimal("500"), new BigDecimal("25.00")));
    }

    /**
     * Dasselbe Wertpapier in zwei Konten desselben Portfolios zählt als ein Segment.
     *
     * {@code findByAccountPortfolioId} liefert eine Position je Konto und Wertpapier. Ohne
     * Zusammenfassung stand das Symbol in beiden Ringen doppelt, und die Tabelle darunter zeigte nur
     * den Anteil eines der beiden Konten.
     */
    @Test
    void simulatePurchaseCountsTheSameSymbolInTwoAccountsOnce() {
        when(portfolioService.getOwnedOrThrow(1L, "hakim")).thenReturn(portfolio("CHF"));
        when(positionRepository.findByAccountPortfolioId(1L)).thenReturn(List.of(
                position("NESN", "CHF", new BigDecimal("6")),
                position("NESN", "CHF", new BigDecimal("4"))));
        when(marketDataProvider.getQuote("NESN")).thenReturn(Optional.of(new Quote("NESN", new BigDecimal("100"), "CHF", null)));
        when(marketDataProvider.getQuote("NEW")).thenReturn(Optional.of(new Quote("NEW", new BigDecimal("100"), "CHF", null)));
        when(marketDataProvider.getInfo("NEW")).thenReturn(Optional.empty());
        lenient().when(fxConversionService.convert(any(), eq("CHF"), eq("CHF"), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService)
                .simulatePurchase(1L, "hakim", "new", new BigDecimal("5"));

        // 6*100 + 4*100 = 1000 in einem Eintrag, nicht 600 und 400 in zwei.
        assertThat(result.currentWeights())
                .extracting(WeightItemDto::symbol, WeightItemDto::value, WeightItemDto::percent)
                .containsExactly(tuple("NESN", new BigDecimal("1000"), new BigDecimal("100.00")));
        assertThat(result.simulatedWeights())
                .extracting(WeightItemDto::symbol, WeightItemDto::value)
                .containsExactly(
                        tuple("NESN", new BigDecimal("1000")),
                        tuple("NEW", new BigDecimal("500")));
    }

    @Test
    void simulatePurchaseConvertsCostToPortfolioBaseCurrencyBeforeSumming() {
        when(portfolioService.getOwnedOrThrow(1L, "farah")).thenReturn(portfolio("CHF"));
        when(positionRepository.findByAccountPortfolioId(1L)).thenReturn(List.of());
        when(marketDataProvider.getQuote("USDSEC")).thenReturn(Optional.of(new Quote("USDSEC", new BigDecimal("100"), "USD", null)));
        when(marketDataProvider.getInfo("USDSEC")).thenReturn(Optional.empty());
        // 100 USD * 10 = 1000 USD, Kurs 0.9 -> 900 CHF.
        when(fxConversionService.convert(eq(new BigDecimal("1000")), eq("USD"), eq("CHF"), any()))
                .thenReturn(new BigDecimal("900"));

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService)
                .simulatePurchase(1L, "farah", "usdsec", new BigDecimal("10"));

        assertThat(result.cost()).isEqualByComparingTo("1000.00");
        assertThat(result.currentPortfolioValue()).isEqualByComparingTo("0.00");
        assertThat(result.simulatedPortfolioValue()).isEqualByComparingTo("900.00");
        assertThat(result.valueChange()).isEqualByComparingTo("900.00");
    }

    @Test
    void backtestComputesGainFromHistoricalPriceToCurrentQuote() {
        LocalDate buyDate = LocalDate.of(2024, 1, 2);
        when(marketDataProvider.getHistorical("AAPL", buyDate, LocalDate.now().minusDays(1), Interval.DAILY))
                .thenReturn(Optional.of(List.of(
                        new HistoricalPrice(buyDate, new BigDecimal("100")),
                        new HistoricalPrice(buyDate.plusDays(1), new BigDecimal("110")))));
        when(marketDataProvider.getQuote("AAPL")).thenReturn(Optional.of(new Quote("AAPL", new BigDecimal("150"), "USD", null)));
        when(priceLookupService.findPriceAtOrBefore("AAPL", buyDate)).thenReturn(Optional.of(new BigDecimal("100")));

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService)
                .backtest("aapl", new BigDecimal("2"), buyDate);

        assertThat(result.priceAtBuy()).isEqualByComparingTo("100.00");
        assertThat(result.currentPrice()).isEqualByComparingTo("150.00");
        assertThat(result.investedAmount()).isEqualByComparingTo("200.00");
        assertThat(result.currentValue()).isEqualByComparingTo("300.00");
        assertThat(result.gainLoss()).isEqualByComparingTo("100.00");
        assertThat(result.returnPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void backtestWithoutHistoricalDataThrowsInvalidSimulationParameter() {
        when(marketDataProvider.getHistorical(eq("NOPE"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());

        var service = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService, priceLookupService);

        assertThatThrownBy(() -> service.backtest("nope", BigDecimal.ONE, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(InvalidSimulationParameterException.class);
    }
}
