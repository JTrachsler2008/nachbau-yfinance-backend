package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ein Cashflow für die MWR-Berechnung: BUY/ACQUISITION negativ, SELL/DIVIDEND positiv, Endwert als
 * letzter (positiver) Cashflow.
 */
public record CashFlow(LocalDate date, BigDecimal amount) {
}
