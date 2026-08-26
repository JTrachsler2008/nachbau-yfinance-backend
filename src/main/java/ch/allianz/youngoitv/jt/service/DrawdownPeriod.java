package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;

/**
 * Ergebnis von {@link RiskService#maxDrawdownPeriod}: die Grösse des Rückgangs zusammen mit den
 * Positionen des vorausgehenden Höchststands und des Tiefpunkts in der übergebenen Reihe.
 *
 * <p>Indizes statt Daten, weil {@link RiskService} bewusst nur über anonyme Wertreihen rechnet - die
 * Zuordnung zu echten Kalendertagen ist Sache der aufrufenden Stelle, die die Reihe aus datierten
 * Kursen zusammengestellt hat.</p>
 */
public record DrawdownPeriod(BigDecimal drawdown, int peakIndex, int troughIndex) {
}
