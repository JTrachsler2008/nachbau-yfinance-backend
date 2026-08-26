package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.PortfolioPositionResponseDto;
import ch.allianz.youngoitv.jt.mapper.PositionMapper;
import ch.allianz.youngoitv.jt.service.PositionService;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bestandsliste eines Portfolios.
 *
 * <p>Das Detail einer einzelnen Position mit ihren offenen FIFO-Tranchen liegt weiterhin im {@link
 * TransactionController} unter {@code /accounts/{accountId}/positions/{securityId}/lots}, weil es aus
 * der Transaktionshistorie berechnet wird und nicht aus der Bestandstabelle.</p>
 */
@RestController
public class PositionController {

    private final PositionService positionService;
    private final PositionMapper positionMapper;

    public PositionController(PositionService positionService, PositionMapper positionMapper) {
        this.positionService = positionService;
        this.positionMapper = positionMapper;
    }

    @GetMapping("/portfolios/{portfolioId}/positions")
    public List<PortfolioPositionResponseDto> forPortfolio(Principal principal, @PathVariable Long portfolioId) {
        var positions = positionService.listForPortfolio(portfolioId, principal.getName());
        var valuations = positionService.valuationsFor(positions);
        return positions.stream()
                .map(position -> positionMapper.toPortfolioResponseDto(position, valuations.get(position.getId())))
                .toList();
    }
}
