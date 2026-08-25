package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Risikoanalyse eines Portfolios ueber einen Zeitraum.
 *
 * <p>Prozentangaben sind fertig skaliert (12.34 = 12.34%), Sharpe Ratio und Beta sind
 * Verhaeltniszahlen. {@code maxDrawdown} und {@code valueAtRisk95} sind negativ, weil sie Verluste
 * beschreiben: -18.20 heisst ein Rueckgang um 18.2% gegenueber dem bisherigen Hoechststand bzw. eine
 * Tagesrendite, die an einem von zwanzig Tagen schlechter als -18.2% ausfaellt.</p>
 *
 * <p>Jede Kennzahl kann {@code null} sein. Das heisst "aus den vorliegenden Daten nicht bestimmbar"
 * und ist absichtlich von einer 0 unterschieden, die das Original in diesen Faellen lieferte: eine
 * Volatilitaet von 0 ist eine fachliche Aussage (ein Wert, der sich nie bewegt), das Fehlen von
 * Kursdaten ist keine.</p>
 *
 * @param observations Anzahl Tagesrenditen, auf denen die Portfoliokennzahlen beruhen
 * @param riskFreeRate risikofreier Zins in Prozent, der in die Sharpe Ratio eingeht
 * @param diversificationBenefit um wie viele Prozentpunkte die Volatilitaet des Portfolios unter der
 *     gewichteten Summe der Einzelvolatilitaeten liegt; {@code null} bei weniger als zwei Wertpapieren
 * @param excluded Wertpapiere ohne verwertbare Daten, siehe {@link RiskExclusionDto}
 */
public record RiskAnalysisResponseDto(
        Long portfolioId,
        String portfolioName,
        String currency,
        LocalDate from,
        LocalDate to,
        String benchmarkSymbol,
        int observations,
        BigDecimal riskFreeRate,
        BigDecimal annualizedReturn,
        BigDecimal volatility,
        BigDecimal sharpeRatio,
        BigDecimal beta,
        BigDecimal maxDrawdown,
        BigDecimal valueAtRisk95,
        BigDecimal diversificationBenefit,
        List<SecurityRiskResponseDto> securities,
        List<RiskExclusionDto> excluded) {
}
