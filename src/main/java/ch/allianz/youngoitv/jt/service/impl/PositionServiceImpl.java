package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PositionService;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final PortfolioService portfolioService;

    public PositionServiceImpl(PositionRepository positionRepository, PortfolioService portfolioService) {
        this.positionRepository = positionRepository;
        this.portfolioService = portfolioService;
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
}
