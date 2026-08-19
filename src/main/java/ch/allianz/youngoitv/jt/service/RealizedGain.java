package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Realisierter Gewinn/Verlust einer einzelnen SELL-Transaktion (Verkaufserlos minus
 * FIFO-Kostenbasis der abgebauten Tranchen), in der Handelswaehrung der Transaktion.
 */
public record RealizedGain(BigDecimal amount, String currency, LocalDate date) {
}
