package ch.allianz.youngoitv.jt.client;

/**
 * Ein Treffer der Symbol-/Namenssuche beim Marktdatenanbieter.
 *
 * {@code quoteType} ist bereits auf die vier Anlagearten dieser Anwendung abgebildet
 * (STOCK/ETF/FUND), nicht der rohe Wert des Anbieters ("EQUITY" etc.) - die Übersetzung passiert an
 * der Quelle, weil nur der Provider weiss, welche seiner Kategorien es überhaupt gibt.
 */
public record SecuritySearchResult(String symbol, String name, String exchange, String quoteType) {
}
