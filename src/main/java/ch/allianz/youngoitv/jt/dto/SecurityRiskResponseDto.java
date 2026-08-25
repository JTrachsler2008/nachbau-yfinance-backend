package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

/**
 * Risikokennzahlen eines einzelnen Wertpapiers im Portfolio.
 *
 * <p>Alle Prozentangaben sind fertig skaliert: 12.34 bedeutet 12.34%, nicht 1234%. Sharpe Ratio und
 * Beta sind Verhaeltniszahlen ohne Einheit. {@code beta} ist {@code null}, wenn es nicht bestimmbar
 * ist (keine Benchmark-Daten oder eine Benchmark ohne Varianz) - bewusst kein Ersatzwert 1.0 wie im
 * Original, weil ein erfundenes Beta von einem berechneten nicht zu unterscheiden waere.</p>
 *
 * @param weight Anteil am Marktwert des Portfolios in Prozent, aus dem letzten verfuegbaren Kurs
 */
public record SecurityRiskResponseDto(
        String symbol,
        String securityName,
        BigDecimal weight,
        BigDecimal annualizedReturn,
        BigDecimal volatility,
        BigDecimal sharpeRatio,
        BigDecimal beta,
        BigDecimal maxDrawdown,
        BigDecimal valueAtRisk95) {
}
