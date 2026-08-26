package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

/**
 * Position in der Bestandsliste eines Portfolios.
 *
 * <p>Bestandsdaten ({@code totalQuantity}, {@code averagePurchasePrice}) kommen aus der Datenbank und
 * sind immer da. {@code currentPrice}, {@code marketValue} und {@code unrealizedGainLoss} hängen an
 * einem externen Kursabruf, der laut Architektur-Plan ausfallen kann - dann bleiben genau diese drei
 * Felder {@code null}, der Rest der Position bleibt trotzdem sichtbar (UI/UX-Plan, degradierter
 * Zustand) statt die ganze Seite an einem fehlenden Kurs scheitern zu lassen.</p>
 *
 * <p>{@code marketValue}/{@code unrealizedGainLoss} in der Handelswährung des Wertpapiers, nicht in
 * der Basiswährung des Portfolios - für eine über Währungen hinweg summierte Zahl siehe
 * {@code GET /portfolios/{id}/valuation}.</p>
 *
 * <p>Ergänzt {@link PositionResponseDto} um Konto und Wertpapierangaben, weil eine Liste über alle
 * Konten eines Portfolios sonst nur IDs enthielte. accountId und securityId werden zusätzlich für
 * den Sprung auf das FIFO-Tranchen-Detail gebraucht
 * ({@code GET /accounts/{accountId}/positions/{securityId}/lots}).</p>
 */
public record PortfolioPositionResponseDto(
        Long id,
        Long accountId,
        String accountName,
        Long securityId,
        String symbol,
        String securityName,
        String tradingCurrency,
        String sector,
        String countryCode,
        BigDecimal totalQuantity,
        BigDecimal averagePurchasePrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedGainLoss) {
}
