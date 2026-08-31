package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

/**
 * Geld- und zeitgewichtete Rendite eines Portfolios, in Prozent.
 *
 * <p>{@code moneyWeightedReturn} braucht nur die tatsächlichen Cashflows und den heutigen Marktwert
 * und ist deshalb hier vollständig. Die zeitgewichtete Rendite braucht mehr, nämlich eine historische
 * Neubewertung an jedem Stichtag; sie steht inzwischen in {@code PortfolioHistoryResponseDto}
 * ({@code GET /portfolios/{id}/history}), wo sie zum Wertverlauf gehört, aus dem sie hervorgeht - und
 * wo sie einen Zeitraum hat, den sie hier nicht hätte.</p>
 *
 * @param timeWeightedReturn immer {@code null}. Das Feld bleibt, damit bestehende Aufrufe nicht
 *     brechen; die Zahl kommt aus {@code /history}
 * @param moneyWeightedReturn {@code null}, wenn weniger als zwei Cashflows vorliegen (kein
 *     Zinsfuss bestimmbar) oder der heutige Marktwert nicht ermittelbar ist
 */
public record PortfolioReturnsResponseDto(
        Long portfolioId,
        String currency,
        BigDecimal timeWeightedReturn,
        BigDecimal moneyWeightedReturn) {
}
