package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;

/**
 * Eine Teilperiode für die TWR-Berechnung: Wert zu Periodenbeginn, externer Cashflow zu
 * Periodenbeginn (z.B. durch BUY/SELL/ACQUISITION/SPLIT ausgelöst) und Wert zu Periodenende.
 */
public record ValuationPeriod(BigDecimal startValue, BigDecimal cashFlow, BigDecimal endValue) {
}
