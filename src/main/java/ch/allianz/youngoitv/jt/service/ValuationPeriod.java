package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;

/**
 * Eine Teilperiode für die TWR-Berechnung: Wert zu Periodenbeginn, externer Cashflow <em>innerhalb</em>
 * der Periode und Wert zu Periodenende.
 *
 * <p>Ein Zufluss ins Portfolio ist positiv, ein Abfluss negativ. Das ist die Sicht des Portfolios und
 * damit umgekehrt zur Sicht der Anlegerin in der geldgewichteten Rendite: ein Kauf bringt Kapital
 * herein, auch wenn er Geld kostet.</p>
 *
 * <p>{@code endValue} enthält den Cashflow bereits - er ist an diesem Tag ja schon gebucht. Genau
 * deshalb zieht {@link TwrService} ihn vom Zuwachs ab, statt ihn zum Anfangswert zu addieren.</p>
 */
public record ValuationPeriod(BigDecimal startValue, BigDecimal cashFlow, BigDecimal endValue) {
}
