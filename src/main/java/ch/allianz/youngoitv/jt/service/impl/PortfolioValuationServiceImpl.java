package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.dto.PortfolioValuationResponseDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PortfolioValuationService;
import ch.allianz.youngoitv.jt.service.PositionService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Siehe {@link PortfolioValuationService}.
 *
 * <p>Kurse fehlen leise (Wertpapier landet in {@code excludedSymbols}), fehlende Wechselkurse
 * werfen: wie in {@link ch.allianz.youngoitv.jt.service.RealizedGainsService} und
 * {@link ch.allianz.youngoitv.jt.service.DividendsService} ist ein Kurs des Marktdatenanbieters eine
 * Grösse, die ausfallen kann, ein Wechselkurs dagegen eine von einer Admin-Person gepflegte Angabe,
 * deren Fehlen eine fachliche Meldung verdient statt eines stillschweigenden Ausschlusses.</p>
 */
@Service
public class PortfolioValuationServiceImpl implements PortfolioValuationService {

    private final PortfolioService portfolioService;
    private final PositionService positionService;
    private final MarketDataProvider marketDataProvider;
    private final FxConversionService fxConversionService;

    public PortfolioValuationServiceImpl(
            PortfolioService portfolioService,
            PositionService positionService,
            MarketDataProvider marketDataProvider,
            FxConversionService fxConversionService) {
        this.portfolioService = portfolioService;
        this.positionService = positionService;
        this.marketDataProvider = marketDataProvider;
        this.fxConversionService = fxConversionService;
    }

    @Override
    public PortfolioValuationResponseDto currentValuation(Long portfolioId, String username) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);
        List<Position> positions = positionService.listForPortfolio(portfolioId, username);
        LocalDate today = LocalDate.now();

        List<String> excludedSymbols = new ArrayList<>();
        Map<String, Optional<Quote>> quoteBySymbol = new LinkedHashMap<>();
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        int held = 0;
        int resolved = 0;

        for (Position position : positions) {
            BigDecimal quantity = position.getTotalQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            held++;
            String symbol = position.getSecurity().getSymbol();
            Optional<Quote> quote = quoteBySymbol.computeIfAbsent(symbol, marketDataProvider::getQuote);
            if (quote.isEmpty()) {
                if (!excludedSymbols.contains(symbol)) {
                    excludedSymbols.add(symbol);
                }
                continue;
            }
            resolved++;
            String tradingCurrency = position.getSecurity().getTradingCurrency();
            BigDecimal positionMarketValue = quantity.multiply(quote.get().price());
            BigDecimal positionCostBasis = quantity.multiply(position.getAveragePurchasePrice());
            marketValue = marketValue.add(
                    fxConversionService.convert(positionMarketValue, tradingCurrency, portfolio.getBaseCurrency(), today));
            costBasis = costBasis.add(
                    fxConversionService.convert(positionCostBasis, tradingCurrency, portfolio.getBaseCurrency(), today));
        }

        // Ohne Bestand ist 0 die zutreffende Antwort; mit Bestand, aber ohne einen einzigen
        // aufgelösten Kurs, ist der Marktwert unbekannt und keine Null (siehe Klassen-Javadoc).
        if (held > 0 && resolved == 0) {
            return new PortfolioValuationResponseDto(
                    portfolio.getId(), portfolio.getBaseCurrency(), null, null, null, excludedSymbols);
        }

        return new PortfolioValuationResponseDto(
                portfolio.getId(),
                portfolio.getBaseCurrency(),
                marketValue,
                costBasis,
                marketValue.subtract(costBasis),
                excludedSymbols);
    }
}
