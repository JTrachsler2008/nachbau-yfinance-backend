package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
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

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService)
                .simulatePurchase(1L, "erik", "new", new BigDecimal("10"));

        // Bestand: 10*100=1000 CHF. Kauf: 10*50=500 CHF. Simuliert gesamt: 1500 CHF.
        assertThat(result.currentPortfolioValue()).isEqualByComparingTo("1000.00");
        assertThat(result.cost()).isEqualByComparingTo("500.00");
        assertThat(result.simulatedPortfolioValue()).isEqualByComparingTo("1500.00");
        assertThat(result.simulatedWeights()).anySatisfy(w -> {
            if (w.symbol().equals("NEW")) {
                assertThat(w.percent()).isEqualByComparingTo("33.33");
            }
        });
    }

    @Test
    void backtestComputesGainFromHistoricalPriceToCurrentQuote() {
        LocalDate buyDate = LocalDate.of(2024, 1, 2);
        when(marketDataProvider.getHistorical("AAPL", buyDate, LocalDate.now().minusDays(1), Interval.DAILY))
                .thenReturn(Optional.of(List.of(
                        new HistoricalPrice(buyDate, new BigDecimal("100")),
                        new HistoricalPrice(buyDate.plusDays(1), new BigDecimal("110")))));
        when(marketDataProvider.getQuote("AAPL")).thenReturn(Optional.of(new Quote("AAPL", new BigDecimal("150"), "USD", null)));

        var result = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService)
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

        var service = new SimulationServiceImpl(portfolioService, positionRepository, marketDataProvider, fxConversionService);

        assertThatThrownBy(() -> service.backtest("nope", BigDecimal.ONE, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(InvalidSimulationParameterException.class);
    }
}
