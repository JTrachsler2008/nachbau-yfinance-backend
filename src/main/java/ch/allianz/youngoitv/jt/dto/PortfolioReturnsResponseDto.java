package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

/**
 * Geld- und zeitgewichtete Rendite eines Portfolios, in Prozent.
 *
 * <p>{@code timeWeightedReturn} ist bewusst noch {@code null}: die dafür nötige Zerlegung der
 * Historie in Teilperioden an jedem Kauf-/Verkaufsdatum, jede mit einer eigenen historischen
 * Neubewertung aller zu diesem Zeitpunkt gehaltenen Wertpapiere, ist eine eigenständige, noch nicht
 * abgeschlossene Arbeit - ein grob konstruierter Wert wäre schlimmer als ein fehlender, weil er sich
 * nicht von einem korrekt berechneten unterscheiden liesse. {@code moneyWeightedReturn} braucht
 * dagegen nur die tatsächlichen Cashflows und den heutigen Marktwert und ist deshalb bereits
 * vollständig.</p>
 *
 * @param moneyWeightedReturn {@code null}, wenn weniger als zwei Cashflows vorliegen (kein
 *     Zinsfuss bestimmbar) oder der heutige Marktwert nicht ermittelbar ist
 */
public record PortfolioReturnsResponseDto(
        Long portfolioId,
        String currency,
        BigDecimal timeWeightedReturn,
        BigDecimal moneyWeightedReturn) {
}
