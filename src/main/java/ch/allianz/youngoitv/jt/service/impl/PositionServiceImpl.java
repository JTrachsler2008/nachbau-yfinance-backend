package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PositionService;
import ch.allianz.youngoitv.jt.service.PositionValuation;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final PortfolioService portfolioService;
    private final MarketDataProvider marketDataProvider;

    public PositionServiceImpl(
            PositionRepository positionRepository,
            PortfolioService portfolioService,
            MarketDataProvider marketDataProvider) {
        this.positionRepository = positionRepository;
        this.portfolioService = portfolioService;
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Position> listForPortfolio(Long portfolioId, String username) {
        portfolioService.getOwnedOrThrow(portfolioId, username);
        // Nach Symbol sortiert, damit die Tabelle bei jedem Aufruf gleich aussieht. Die Reihenfolge aus
        // der Datenbank ist ohne ORDER BY nicht zugesichert.
        return positionRepository.findByAccountPortfolioId(portfolioId).stream()
                .sorted(Comparator.comparing(position -> position.getSecurity().getSymbol()))
                .toList();
    }

    @Override
    public Map<Long, PositionValuation> valuationsFor(List<Position> positions) {
        // Der Kurs wird je Symbol zwischengespeichert, die Bewertung selbst aber je Position
        // gerechnet: dasselbe Wertpapier auf zwei Konten desselben Portfolios braucht nur einen
        // Kursabruf, hat aber unterschiedliche Mengen und Einstandspreise.
        Map<String, Optional<Quote>> quoteBySymbol = new LinkedHashMap<>();
        Map<Long, PositionValuation> result = new LinkedHashMap<>();
        for (Position position : positions) {
            String symbol = position.getSecurity().getSymbol();
            Optional<Quote> quote = quoteBySymbol.computeIfAbsent(symbol, marketDataProvider::getQuote);
            result.put(position.getId(), valuationFor(position, quote));
        }
        return result;
    }

    private PositionValuation valuationFor(Position position, Optional<Quote> quote) {
        return quote.map(q -> {
                    BigDecimal marketValue = position.getTotalQuantity().multiply(q.price());
                    BigDecimal costBasis = position.getTotalQuantity().multiply(position.getAveragePurchasePrice());
                    return new PositionValuation(q.price(), marketValue, marketValue.subtract(costBasis));
                })
                .orElseGet(PositionValuation::unavailable);
    }
}
