package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

/**
 * Position in der Bestandsliste eines Portfolios.
 *
 * <p>Enthält bewusst nur Bestandsdaten aus der Datenbank und keinen aktuellen Kurs, keinen Marktwert
 * und keinen Gewinn. Diese Werte hängen an externen Kursabrufen, die laut Architektur-Plan
 * ausfallen können; die Oberfläche soll den Bestand trotzdem anzeigen (UI/UX-Plan, degradierter
 * Zustand) statt die ganze Seite an einem fehlenden Kurs scheitern zu lassen.</p>
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
        BigDecimal averagePurchasePrice) {
}
