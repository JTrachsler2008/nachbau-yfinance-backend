package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;

/**
 * Eine Teilperiode fuer die TWR-Berechnung: Wert zu Periodenbeginn, externer Cashflow zu
 * Periodenbeginn (z.B. durch BUY/SELL/ACQUISITION/SPLIT ausgeloest) und Wert zu Periodenende.
 */
public record ValuationPeriod(BigDecimal startValue, BigDecimal cashFlow, BigDecimal endValue) {
}
