package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.PortfolioReturnsResponseDto;

/** Siehe {@link ch.allianz.youngoitv.jt.dto.PortfolioReturnsResponseDto}. */
public interface PortfolioReturnsService {

    /**
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn es das Portfolio nicht gibt
     * @throws ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException wenn es einem anderen Benutzer gehört
     * @throws ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException wenn für eine Buchung kein
     *     Wechselkurs zur Basiswährung hinterlegt ist
     */
    PortfolioReturnsResponseDto returns(Long portfolioId, String username);
}
