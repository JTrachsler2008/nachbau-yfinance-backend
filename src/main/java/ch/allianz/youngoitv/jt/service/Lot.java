package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Eine offene Kauf-Charge (FIFO-Tranche): Menge, Kaufpreis und Kaufdatum. Kein persistiertes
 * Domaenenobjekt, sondern das Ergebnis von {@link FifoLotService#calculateOpenLots}.
 */
public record Lot(BigDecimal quantity, BigDecimal purchasePrice, LocalDate purchaseDate) {
}
