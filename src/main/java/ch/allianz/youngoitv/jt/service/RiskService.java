package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Risikokennzahlen auf Basis taeglicher Renditen bzw. einer Wertreihe. Reine Funktionen ueber
 * uebergebene Reihen (keine eigenen Kursabrufe), damit sie mit deterministischen Testdaten statt
 * Live-Kursen verifiziert werden koennen. Annualisierung mit dem Faktor 252 Handelstage, Sharpe
 * Ratio mit konfigurierbarem risikofreiem Zins (Default 4% p.a. wie im Original).
 */
@Service
public class RiskService {

    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final BigDecimal DEFAULT_RISK_FREE_RATE = new BigDecimal("0.04");

    public BigDecimal annualizedVolatility(List<BigDecimal> dailyReturns) {
        double dailyStdDev = standardDeviation(dailyReturns);
        return BigDecimal.valueOf(dailyStdDev * Math.sqrt(TRADING_DAYS_PER_YEAR));
    }

    /**
     * Geometrisch annualisierte Rendite (CAGR) einer Reihe von Tagesrenditen. Die Renditen werden
     * verkettet und das Ergebnis auf 252 Handelstage hochgerechnet, es ist also nicht das 252-fache
     * des Mittelwerts: ein Plus von 10% und danach ein Minus von 10% ergeben zusammen -1% und nicht 0%.
     *
     * <p>Faellt der verkettete Wert auf 0 oder darunter, ist das Kapital aufgebraucht; dann gilt
     * -1.0 (-100%), weil eine Wurzel aus einer negativen Zahl keine Rendite ergaebe.</p>
     *
     * <p>Bei sehr kurzen Reihen ist die Hochrechnung mathematisch korrekt, aber wenig belastbar: aus
     * zwanzig Tagen wird mit dem Exponenten 12.6 hochgerechnet. Wer die Reihe zusammenstellt, sollte
     * daher eine Mindestlaenge verlangen.</p>
     */
    public BigDecimal annualizedReturn(List<BigDecimal> dailyReturns) {
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double growth = 1.0;
        for (BigDecimal dailyReturn : dailyReturns) {
            growth *= 1.0 + dailyReturn.doubleValue();
        }
        if (growth <= 0.0) {
            return BigDecimal.valueOf(-1.0);
        }
        double years = (double) dailyReturns.size() / TRADING_DAYS_PER_YEAR;
        return BigDecimal.valueOf(Math.pow(growth, 1.0 / years) - 1.0);
    }

    public BigDecimal sharpeRatio(List<BigDecimal> dailyReturns) {
        return sharpeRatio(dailyReturns, DEFAULT_RISK_FREE_RATE);
    }

    public BigDecimal sharpeRatio(List<BigDecimal> dailyReturns, BigDecimal annualRiskFreeRate) {
        double annualizedReturn = mean(dailyReturns) * TRADING_DAYS_PER_YEAR;
        double annualizedVol = standardDeviation(dailyReturns) * Math.sqrt(TRADING_DAYS_PER_YEAR);
        if (annualizedVol == 0.0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((annualizedReturn - annualRiskFreeRate.doubleValue()) / annualizedVol);
    }

    public BigDecimal beta(List<BigDecimal> portfolioReturns, List<BigDecimal> benchmarkReturns) {
        if (portfolioReturns.size() != benchmarkReturns.size() || portfolioReturns.isEmpty()) {
            throw new IllegalArgumentException("Portfolio- und Benchmark-Renditen muessen gleich lang und nicht leer sein");
        }
        double benchmarkMean = mean(benchmarkReturns);
        double portfolioMean = mean(portfolioReturns);
        double covariance = 0.0;
        double benchmarkVariance = 0.0;
        for (int i = 0; i < portfolioReturns.size(); i++) {
            double p = portfolioReturns.get(i).doubleValue() - portfolioMean;
            double b = benchmarkReturns.get(i).doubleValue() - benchmarkMean;
            covariance += p * b;
            benchmarkVariance += b * b;
        }
        if (benchmarkVariance == 0.0) {
            throw new IllegalArgumentException("Benchmark-Renditen haben keine Varianz - Beta nicht definiert");
        }
        return BigDecimal.valueOf(covariance / benchmarkVariance);
    }

    /**
     * Grösster Peak-to-Trough-Verlust einer Wertreihe, als negativer Prozentwert (z.B. -0.25 fuer
     * einen Verlust von 25% gegenueber dem bisherigen Hoechststand).
     */
    public BigDecimal maxDrawdown(List<BigDecimal> valueSeries) {
        if (valueSeries.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double peak = valueSeries.get(0).doubleValue();
        double worstDrawdown = 0.0;
        for (BigDecimal value : valueSeries) {
            double current = value.doubleValue();
            peak = Math.max(peak, current);
            double drawdown = (current - peak) / peak;
            worstDrawdown = Math.min(worstDrawdown, drawdown);
        }
        return BigDecimal.valueOf(worstDrawdown);
    }

    /**
     * Historischer VaR 95%: das 5%-Perzentil der taeglichen Renditen (Nearest-Rank-Verfahren).
     */
    public BigDecimal valueAtRisk95(List<BigDecimal> dailyReturns) {
        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = dailyReturns.stream().sorted().toList();
        int rank = (int) Math.ceil(0.05 * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return sorted.get(index);
    }

    private double mean(List<BigDecimal> values) {
        return values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
    }

    private double standardDeviation(List<BigDecimal> values) {
        double mean = mean(values);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}
