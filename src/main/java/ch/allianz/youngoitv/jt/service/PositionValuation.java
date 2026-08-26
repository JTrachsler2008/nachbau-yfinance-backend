package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;

/**
 * Live-Bewertung einer Position, in der Handelswährung des Wertpapiers.
 *
 * <p>Alle drei Felder {@code null}, wenn der Marktdatenanbieter keinen Kurs liefert - eine
 * Kursänderung von 0 wäre eine Behauptung, die die Daten nicht stützen. Der Bestand selbst
 * ({@code totalQuantity}, {@code averagePurchasePrice}) bleibt davon unberührt und immer sichtbar,
 * siehe {@code PortfolioPositionResponseDto}.</p>
 */
public record PositionValuation(BigDecimal currentPrice, BigDecimal marketValue, BigDecimal unrealizedGainLoss) {

    public static PositionValuation unavailable() {
        return new PositionValuation(null, null, null);
    }
}
