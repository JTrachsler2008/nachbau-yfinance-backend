package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ein Cashflow für die MWR-Berechnung: BUY negativ, SELL/DIVIDEND positiv, Endwert als letzter
 * (positiver) Cashflow. SPLIT, ACQUISITION und MERGER erzeugen keinen Cashflow -
 * {@code TransactionServiceImpl.applyAcquisitionOrMerger} rührt den Kontostand nicht an, es ist eine
 * reine Neubewertung des Bestands, kein Geldfluss.
 */
public record CashFlow(LocalDate date, BigDecimal amount) {
}
