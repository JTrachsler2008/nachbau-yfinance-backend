package ch.allianz.youngoitv.jt.entity;

/**
 * Art einer Buchung.
 *
 * <p>COUPON und REDEMPTION gehören zu den Anleihen. Ein Coupon ist die laufende Zinszahlung und
 * lässt den Bestand unverändert, eine Rückzahlung baut ihn ab und bringt den Rückzahlungsbetrag aufs
 * Konto. Beide sind eigene Werte und keine Sonderfälle von DIVIDEND und SELL: sonst stünde der
 * Zinsertrag in der Dividendensumme, und eine Rückzahlung wäre in der Historie nicht von einem
 * Verkauf am Markt zu unterscheiden.
 *
 * <p>Als {@code EnumType.STRING} in einer {@code VARCHAR(20)}-Spalte ohne Check-Constraint
 * gespeichert (siehe {@code V2__create_domain_tables.sql}), neue Werte brauchen deshalb keine
 * Migration. Neue Werte kommen hinten dazu, damit sich der Ordinalwert bestehender Werte nicht
 * verschiebt.
 */
public enum TransactionType {
    BUY,
    SELL,
    DIVIDEND,
    SPLIT,
    ACQUISITION,
    MERGER,
    /** Zinszahlung einer Anleihe: Bestand unverändert, Betrag abzüglich Gebühr und Steuer aufs Konto. */
    COUPON,
    /** Rückzahlung bei Fälligkeit: Bestand geht um die Menge ab, Rückzahlungsbetrag aufs Konto. */
    REDEMPTION
}
