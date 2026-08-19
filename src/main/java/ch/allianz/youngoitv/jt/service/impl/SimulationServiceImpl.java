package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.BacktestChartPointDto;
import ch.allianz.youngoitv.jt.dto.BacktestResponseDto;
import ch.allianz.youngoitv.jt.dto.PurchaseSimulationResponseDto;
import ch.allianz.youngoitv.jt.dto.WeightItemDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.SimulationService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Rein lesende Was-waere-wenn-Berechnungen ohne Persistenzeffekt (YOUNGOITV-437): weder
 * {@code simulatePurchase} noch {@code backtest} legen eine Transaction/Position/Security an - beide
 * arbeiten ausschliesslich mit Live-/historischen Kursen aus {@link MarketDataProvider}.
 */
@Service
public class SimulationServiceImpl implements SimulationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 4;
    private static final int RESULT_SCALE = 2;

    private final PortfolioService portfolioService;
    private final PositionRepository positionRepository;
    private final MarketDataProvider marketDataProvider;
    private final FxConversionService fxConversionService;

    public SimulationServiceImpl(
            PortfolioService portfolioService,
            PositionRepository positionRepository,
            MarketDataProvider marketDataProvider,
            FxConversionService fxConversionService) {
        this.portfolioService = portfolioService;
        this.positionRepository = positionRepository;
        this.marketDataProvider = marketDataProvider;
        this.fxConversionService = fxConversionService;
    }

    @Override
    public PurchaseSimulationResponseDto simulatePurchase(
            Long portfolioId, String username, String symbol, BigDecimal quantity) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);
        String upperSymbol = symbol.toUpperCase();

        BigDecimal currentPrice = marketDataProvider.getQuote(upperSymbol)
                .map(quote -> quote.price())
                .orElseThrow(() -> new InvalidSimulationParameterException("No live quote available for " + upperSymbol));
        String securityName = marketDataProvider.getInfo(upperSymbol).map(info -> info.name()).orElse(upperSymbol);

        BigDecimal cost = currentPrice.multiply(quantity);

        List<Position> positions = positionRepository.findByAccountPortfolioId(portfolioId);
        List<WeightEntry> valuedPositions = new ArrayList<>();
        BigDecimal currentTotal = BigDecimal.ZERO;
        for (Position position : positions) {
            BigDecimal value = valuePosition(position, portfolio.getBaseCurrency());
            if (value != null) {
                currentTotal = currentTotal.add(value);
                valuedPositions.add(new WeightEntry(position.getSecurity().getSymbol(), value));
            }
        }

        BigDecimal simulatedTotal = currentTotal.add(cost);

        List<WeightItemDto> currentWeights = toWeightItems(valuedPositions, currentTotal);

        List<WeightItemDto> simulatedWeights = new ArrayList<>();
        for (WeightEntry entry : valuedPositions) {
            simulatedWeights.add(new WeightItemDto(entry.symbol(), entry.value(), percentOf(entry.value(), simulatedTotal)));
        }
        simulatedWeights.add(new WeightItemDto(upperSymbol, cost, percentOf(cost, simulatedTotal)));

        BigDecimal returnChangePercent = percentOf(cost, currentTotal);

        return new PurchaseSimulationResponseDto(
                upperSymbol,
                securityName,
                currentPrice.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                quantity,
                cost.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                currentTotal.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                simulatedTotal.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                cost.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                returnChangePercent,
                currentWeights,
                simulatedWeights);
    }

    @Override
    public BacktestResponseDto backtest(String symbol, BigDecimal quantity, LocalDate purchaseDate) {
        String upperSymbol = symbol.toUpperCase();
        LocalDate endDate = LocalDate.now().minusDays(1);
        if (purchaseDate.isAfter(endDate)) {
            throw new InvalidSimulationParameterException("purchaseDate must be in the past");
        }

        List<HistoricalPrice> prices = marketDataProvider.getHistorical(upperSymbol, purchaseDate, endDate, Interval.DAILY)
                .orElseThrow(() -> new InvalidSimulationParameterException("No historical prices available for " + upperSymbol))
                .stream()
                .sorted(Comparator.comparing(HistoricalPrice::date))
                .toList();
        if (prices.isEmpty()) {
            throw new InvalidSimulationParameterException("No historical prices available for " + upperSymbol);
        }

        BigDecimal priceAtBuy = prices.get(0).close();
        List<BacktestChartPointDto> chartData = new ArrayList<>();
        for (HistoricalPrice price : prices) {
            chartData.add(new BacktestChartPointDto(price.date(), price.close(), price.close().multiply(quantity)));
        }

        BigDecimal currentPrice = marketDataProvider.getQuote(upperSymbol)
                .map(quote -> quote.price())
                .orElse(prices.get(prices.size() - 1).close());

        BigDecimal invested = priceAtBuy.multiply(quantity);
        BigDecimal currentValue = currentPrice.multiply(quantity);
        BigDecimal gainLoss = currentValue.subtract(invested);
        BigDecimal returnPercent = percentOf(gainLoss, invested);

        return new BacktestResponseDto(
                upperSymbol,
                purchaseDate,
                quantity,
                priceAtBuy.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                currentPrice.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                invested.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                currentValue.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                gainLoss.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                returnPercent,
                chartData);
    }

    private BigDecimal valuePosition(Position position, String portfolioCurrency) {
        return marketDataProvider.getQuote(position.getSecurity().getSymbol())
                .map(quote -> {
                    BigDecimal valueInTradingCurrency = quote.price().multiply(position.getTotalQuantity());
                    return fxConversionService.convert(
                            valueInTradingCurrency, position.getSecurity().getTradingCurrency(),
                            portfolioCurrency, LocalDate.now());
                })
                .orElse(null);
    }

    private List<WeightItemDto> toWeightItems(List<WeightEntry> entries, BigDecimal total) {
        List<WeightItemDto> items = new ArrayList<>();
        for (WeightEntry entry : entries) {
            items.add(new WeightItemDto(entry.symbol(), entry.value(), percentOf(entry.value(), total)));
        }
        return items;
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(total, SCALE, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private record WeightEntry(String symbol, BigDecimal value) {
    }
}
