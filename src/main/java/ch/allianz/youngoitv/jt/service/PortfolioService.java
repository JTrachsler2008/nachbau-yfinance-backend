package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.PortfolioCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioUpdateRequestDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import java.util.List;

public interface PortfolioService {

    Portfolio create(String username, PortfolioCreateRequestDto request);

    List<Portfolio> listOwnedBy(String username);

    /**
     * Mandate eines Portfolio-Managers: die Portfolios anderer Benutzer, für die er als Manager
     * eingetragen ist. Getrennt von {@link #listOwnedBy(String)}, weil die Oberfläche eigene und
     * betreute Portfolios unterscheidbar anzeigen soll (User-Rollen-Plan, Abschnitt Auswirkung auf
     * UI/UX).
     */
    List<Portfolio> listManagedBy(String username);

    Portfolio getOwnedOrThrow(Long portfolioId, String username);

    Portfolio update(Long portfolioId, String username, PortfolioUpdateRequestDto request);

    void delete(Long portfolioId, String username);

    Portfolio assignManager(Long portfolioId, String ownerUsername, Long managerUserId);
}
