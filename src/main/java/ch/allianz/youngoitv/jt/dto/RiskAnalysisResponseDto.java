package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Risikoanalyse eines Portfolios über einen Zeitraum.
 *
 * <p>Prozentangaben sind fertig skaliert (12.34 = 12.34%), Sharpe Ratio und Beta sind
 * Verhältniszahlen. {@code maxDrawdown} und {@code valueAtRisk95} sind negativ, weil sie Verluste
 * beschreiben: -18.20 heisst ein Rückgang um 18.2% gegenüber dem bisherigen Höchststand bzw. eine
 * Tagesrendite, die an einem von zwanzig Tagen schlechter als -18.2% ausfällt.</p>
 *
 * <p>Jede Kennzahl kann {@code null} sein. Das heisst "aus den vorliegenden Daten nicht bestimmbar"
 * und ist absichtlich von einer 0 unterschieden, die das Original in diesen Fällen lieferte: eine
 * Volatilität von 0 ist eine fachliche Aussage (ein Wert, der sich nie bewegt), das Fehlen von
 * Kursdaten ist keine.</p>
 *
 * @param benchmarkReturn annualisierte Rendite der Benchmark im Zeitraum in Prozent; {@code null},
 *     wenn zur Benchmark keine verwertbaren Kursdaten vorliegen
 * @param benchmarkVolatility annualisierte Volatilität der Benchmark im Zeitraum in Prozent. Zusammen
 *     mit {@code benchmarkReturn} der Bezugspunkt, ohne den die Kennzahlen des Portfolios keine
 *     Einordnung haben: eine Volatilität von 18% ist je nach Marktphase hoch oder niedrig
 * @param observations Anzahl Tagesrenditen, auf denen die Portfoliokennzahlen beruhen
 * @param riskFreeRate risikofreier Zins in Prozent, der in die Sharpe Ratio eingeht
 * @param diversificationBenefit um wie viele Prozentpunkte die Volatilität des Portfolios unter der
 *     gewichteten Summe der Einzelvolatilitäten liegt; {@code null} bei weniger als zwei Wertpapieren
 * @param excluded Wertpapiere ohne verwertbare Daten, siehe {@link RiskExclusionDto}
 */
public record RiskAnalysisResponseDto(
        Long portfolioId,
        String portfolioName,
        String currency,
        LocalDate from,
        LocalDate to,
        String benchmarkSymbol,
        BigDecimal benchmarkReturn,
        BigDecimal benchmarkVolatility,
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
