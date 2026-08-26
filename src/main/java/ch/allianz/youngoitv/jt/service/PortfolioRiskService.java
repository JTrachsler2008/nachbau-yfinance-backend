package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;
import java.time.LocalDate;

/**
 * Stellt die Renditereihen eines Portfolios aus historischen Kursen zusammen und lässt
 * {@link RiskService} darauf rechnen.
 *
 * <p>Die Trennung ist beabsichtigt: {@link RiskService} bleibt eine Sammlung reiner Funktionen über
 * übergebene Reihen und damit ohne Testdoubles prüfbar, während hier die Beschaffung der Daten
 * liegt (Bestände, Kurshistorie, Wechselkurse) samt aller Fälle, in denen sie fehlen.</p>
 */
public interface PortfolioRiskService {

    /**
     * Risikoanalyse eines Portfolios über den Zeitraum {@code [from, to]}.
     *
     * <p>Die Auflösung von Presets (z.B. "1 Jahr zurück") in konkrete Daten ist Sache des Controllers
     * - dieser Dienst kennt nur noch den fertigen Zeitraum, ob er aus einem Preset oder einer freien
     * Eingabe stammt, macht für die Berechnung keinen Unterschied.</p>
     *
     * @param benchmarkSymbol Referenz für das Beta, z.B. {@code SPY}
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn es das Portfolio nicht gibt
     * @throws ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException wenn es einem anderen Benutzer gehört
     */
    RiskAnalysisResponseDto analyse(
            Long portfolioId, String username, LocalDate from, LocalDate to, String benchmarkSymbol);
}
