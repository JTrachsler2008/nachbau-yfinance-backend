package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.Position;
import java.util.List;
import java.util.Map;

/**
 * Lesender Zugriff auf Bestände.
 *
 * <p>Die Bestände selbst werden von {@code TransactionServiceImpl} beim Buchen fortgeschrieben, dieser
 * Dienst schreibt nichts. Er existiert, damit die Bestandsliste eines Portfolios nicht direkt aus dem
 * Controller ins Repository greift und die Eigentumsprüfung an einer Stelle liegt.</p>
 */
public interface PositionService {

    /**
     * Alle Bestände über alle Konten eines Portfolios.
     *
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn es das Portfolio nicht gibt
     * @throws ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException wenn es einem anderen Benutzer gehört
     */
    List<Position> listForPortfolio(Long portfolioId, String username);

    /**
     * Live-Bewertung je Position, Schlüssel ist die Position-ID.
     *
     * <p>Getrennt von {@link #listForPortfolio}, damit ein Aufrufer, der nur die Bestandsdaten aus der
     * Datenbank braucht (z.B. {@code PortfolioRiskServiceImpl}, das ohnehin selbst Kurshistorien holt),
     * nicht zusätzlich einen Livekurs pro Position abruft, den er gar nicht verwendet.</p>
     */
    Map<Long, PositionValuation> valuationsFor(List<Position> positions);
}
