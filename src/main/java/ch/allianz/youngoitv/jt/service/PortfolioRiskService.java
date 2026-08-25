package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;

/**
 * Stellt die Renditereihen eines Portfolios aus historischen Kursen zusammen und laesst
 * {@link RiskService} darauf rechnen.
 *
 * <p>Die Trennung ist beabsichtigt: {@link RiskService} bleibt eine Sammlung reiner Funktionen ueber
 * uebergebene Reihen und damit ohne Testdoubles pruefbar, waehrend hier die Beschaffung der Daten
 * liegt (Bestaende, Kurshistorie, Wechselkurse) samt aller Faelle, in denen sie fehlen.</p>
 */
public interface PortfolioRiskService {

    /**
     * Risikoanalyse eines Portfolios ueber die letzten {@code lookbackDays} Kalendertage.
     *
     * @param benchmarkSymbol Referenz fuer das Beta, z.B. {@code SPY}
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn es das Portfolio nicht gibt
     * @throws ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException wenn es einem anderen Benutzer gehoert
     */
    RiskAnalysisResponseDto analyse(Long portfolioId, String username, int lookbackDays, String benchmarkSymbol);
}
