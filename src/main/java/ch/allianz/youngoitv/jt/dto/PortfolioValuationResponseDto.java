package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Live-Bewertung des gesamten Portfolios, umgerechnet in die Basiswährung.
 *
 * <p>{@code marketValue}, {@code costBasis} und {@code unrealizedGainLoss} sind {@code null}, wenn
 * für kein einziges Wertpapier ein Kurs vorliegt (nicht 0 - siehe {@link
 * ch.allianz.youngoitv.jt.service.PositionValuation}). Halten Position und Portfolio gemischt
 * bewertbare und nicht bewertbare Titel, summieren die drei Felder nur über die bewertbaren; die
 * übrigen stehen in {@code excludedSymbols}, damit die Summe nicht als vollständig missverstanden
 * wird.</p>
 */
public record PortfolioValuationResponseDto(
        Long portfolioId,
        String currency,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedGainLoss,
        List<String> excludedSymbols) {
}
