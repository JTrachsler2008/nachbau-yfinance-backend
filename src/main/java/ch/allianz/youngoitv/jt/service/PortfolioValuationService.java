package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.PortfolioValuationResponseDto;

/**
 * Marktwert, Einstand und unrealisierter Gewinn/Verlust eines Portfolios in seiner Basiswährung.
 *
 * <p>Schliesst die in {@code PerformanceController} als Folgearbeit vermerkte Lücke: die
 * Bestandsliste selbst lieferte bisher keine über Konten und Währungen aggregierte Zahl, weil dafür
 * sowohl ein Livekurs je Wertpapier als auch eine Währungsumrechnung nötig sind.</p>
 */
public interface PortfolioValuationService {

    /**
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn es das Portfolio nicht gibt
     * @throws ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException wenn es einem anderen Benutzer gehört
     * @throws ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException wenn für ein bewertbares
     *     Wertpapier kein Wechselkurs zur Basiswährung hinterlegt ist
     */
    PortfolioValuationResponseDto currentValuation(Long portfolioId, String username);
}
