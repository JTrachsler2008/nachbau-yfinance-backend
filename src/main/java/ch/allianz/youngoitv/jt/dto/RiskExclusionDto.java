package ch.allianz.youngoitv.jt.dto;

/**
 * Ein Wertpapier, das nicht in die Risikoanalyse eingehen konnte, samt Grund.
 *
 * <p>Existiert, damit ein Loch in den Kursdaten sichtbar bleibt. Das Original liess betroffene
 * Positionen still weg, wodurch eine Volatilität über zwei von fünf Wertpapieren aussah wie eine
 * über das ganze Portfolio.</p>
 *
 * <p>Der Grund ist ein stabiler Code und kein Satz, damit die Oberfläche ihn übersetzen kann:
 * {@code NO_PRICE_HISTORY} (der Kursanbieter liefert keine Historie), {@code TOO_FEW_OBSERVATIONS}
 * (zu wenige Handelstage im Zeitraum) oder {@code NO_FX_RATE} (kein Wechselkurs in die Währung des
 * Portfolios, das Gewicht ist damit unbekannt).</p>
 */
public record RiskExclusionDto(String symbol, String reason) {
}
