package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.PortfolioHistoryResponseDto;
import java.time.LocalDate;

/**
 * Historischer Wertverlauf eines Portfolios samt zeitgewichteter Rendite (TWR).
 *
 * <p>Der Unterschied zur Risikoanalyse ist der wesentliche Punkt: dort wird der <em>heutige</em>
 * Bestand über den Zeitraum zurückprojiziert, hier wird der Bestand <em>jedes Tages</em> aus der
 * Transaktionshistorie rekonstruiert und mit dem Kurs dieses Tages bewertet. Nur so ist die Linie
 * der Verlauf des damaligen Portfolios und nicht der eines Portfolios, das es damals nicht gab.</p>
 */
public interface PortfolioHistoryService {

    /**
     * @param benchmarkSymbol Referenz für die Vergleichslinie; fehlt ihre Historie, bleibt die Linie
     *     leer und das Symbol erscheint in {@code excluded}
     */
    PortfolioHistoryResponseDto history(
            Long portfolioId, String username, LocalDate from, LocalDate to, String benchmarkSymbol);
}
