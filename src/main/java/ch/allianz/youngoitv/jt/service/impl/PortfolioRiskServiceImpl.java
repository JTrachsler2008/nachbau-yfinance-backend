package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;
import ch.allianz.youngoitv.jt.dto.RiskExclusionDto;
import ch.allianz.youngoitv.jt.dto.SecurityRiskResponseDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.service.PortfolioRiskService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PositionService;
import ch.allianz.youngoitv.jt.service.RiskService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Zusammenstellung der Renditereihen eines Portfolios aus Kurshistorien und Auswertung durch
 * {@link RiskService}. Damit wird die in {@code PerformanceController} als Folgearbeit vermerkte
 * Luecke geschlossen: die Kennzahlen aus YOUNGOITV-434 hatten bisher keinen Aufrufer.
 *
 * <p>Wie im Original wird die <em>heutige</em> Zusammensetzung des Portfolios ueber den Zeitraum
 * zurueckprojiziert. Die Kennzahlen beantworten also "wie riskant ist der heutige Bestand, gemessen
 * an seinem Verhalten im letzten Jahr", nicht "wie riskant war das Portfolio damals". Der Unterschied
 * ist erheblich, sobald im Zeitraum umgeschichtet wurde, und laesst sich ohne eine vollstaendige
 * historische Neubewertung aus der Transaktionshistorie nicht beheben.</p>
 *
 * <p>Zwei bewusste Abweichungen vom Original. Erstens werden die Reihen mehrerer Wertpapiere ueber die
 * <em>Schnittmenge der Handelstage</em> ausgerichtet und nicht ueber die Position vom Ende her: bei
 * Titeln von Boersen mit verschiedenen Feiertagen paart die Ausrichtung ueber den Index sonst Tage,
 * die Wochen auseinanderliegen. Zweitens verschwindet ein Wertpapier ohne verwertbare Kursdaten nicht
 * still, sondern erscheint in {@code excluded} - eine Volatilitaet ueber zwei von fuenf Titeln sah im
 * Original aus wie eine ueber das ganze Portfolio.</p>
 *
 * <p>Grenze der Methode: gerechnet wird mit Kursrenditen in der jeweiligen Handelswaehrung. Ein
 * CHF-Portfolio mit US-Titeln traegt damit ein Waehrungsrisiko, das hier nicht abgebildet ist. Eine
 * taegliche FX-Umrechnung wuerde daran nichts aendern, weil {@code fx_rates} eine manuell gepflegte
 * Tabelle mit einzelnen Stichtagen ist: der fortgeschriebene letzte bekannte Kurs haette eine
 * Wechselkurs-Volatilitaet von 0 und damit nur den Anschein von Genauigkeit. Die Wechselkurse gehen
 * deshalb nur in die Gewichte ein, wo sie tatsaechlich gebraucht werden.</p>
 */
@Service
public class PortfolioRiskServiceImpl implements PortfolioRiskService {

    /**
     * Untergrenze fuer eine Kennzahl aus Tagesrenditen. Weniger als 20 Handelstage ergeben zwar eine
     * rechenbare Zahl, aber der Faktor sqrt(252) macht daraus eine Jahresaussage aus knapp einem Monat.
     */
    static final int MIN_OBSERVATIONS = 20;

    static final String NO_PRICE_HISTORY = "NO_PRICE_HISTORY";
    static final String TOO_FEW_OBSERVATIONS = "TOO_FEW_OBSERVATIONS";
    static final String NO_FX_RATE = "NO_FX_RATE";

    /**
     * Risikofreier Zins wie im Original (4% p.a.). Wird explizit an
     * {@link RiskService#sharpeRatio(List, BigDecimal)} uebergeben und im Ergebnis mitgeliefert, damit
     * die ausgewiesene Annahme und die gerechnete nicht auseinanderlaufen koennen.
     */
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.04");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RESULT_SCALE = 2;
    private static final int INTERNAL_SCALE = 10;

    private final PortfolioService portfolioService;
    private final PositionService positionService;
    private final MarketDataProvider marketDataProvider;
    private final FxConversionService fxConversionService;
    private final RiskService riskService;

    public PortfolioRiskServiceImpl(
            PortfolioService portfolioService,
            PositionService positionService,
            MarketDataProvider marketDataProvider,
            FxConversionService fxConversionService,
            RiskService riskService) {
        this.portfolioService = portfolioService;
        this.positionService = positionService;
        this.marketDataProvider = marketDataProvider;
        this.fxConversionService = fxConversionService;
        this.riskService = riskService;
    }

    @Override
    public RiskAnalysisResponseDto analyse(
            Long portfolioId, String username, int lookbackDays, String benchmarkSymbol) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(lookbackDays);

        List<RiskExclusionDto> excluded = new ArrayList<>();
        Map<String, SymbolSeries> series = new LinkedHashMap<>();
        for (Holding holding : holdings(portfolioId, username)) {
            NavigableMap<LocalDate, BigDecimal> closes = closes(holding.symbol(), from, to);
            if (closes.isEmpty()) {
                excluded.add(new RiskExclusionDto(holding.symbol(), NO_PRICE_HISTORY));
                continue;
            }
            if (closes.size() - 1 < MIN_OBSERVATIONS) {
                excluded.add(new RiskExclusionDto(holding.symbol(), TOO_FEW_OBSERVATIONS));
                continue;
            }
            BigDecimal marketValue = marketValue(holding, closes, portfolio.getBaseCurrency());
            if (marketValue == null) {
                excluded.add(new RiskExclusionDto(holding.symbol(), NO_FX_RATE));
                continue;
            }
            series.put(holding.symbol(), new SymbolSeries(holding, closes, dailyReturns(closes), marketValue));
        }

        NavigableMap<LocalDate, BigDecimal> benchmarkReturns = benchmarkReturns(benchmarkSymbol, from, to, excluded);
        // Rendite und Volatilitaet der Benchmark gehoeren ins Ergebnis, weil eine Volatilitaet von 18%
        // je nach Marktphase hoch oder niedrig ist. Ohne den Bezugspunkt muesste die Oberflaeche eine
        // Einordnung behaupten, die in der Zahl nicht steckt.
        List<BigDecimal> benchmarkSeries =
                benchmarkReturns == null ? List.of() : List.copyOf(benchmarkReturns.values());
        BigDecimal benchmarkReturn =
                benchmarkReturns == null ? null : asPercent(riskService.annualizedReturn(benchmarkSeries));
        BigDecimal benchmarkVolatility =
                benchmarkReturns == null ? null : asPercent(riskService.annualizedVolatility(benchmarkSeries));

        BigDecimal totalValue = series.values().stream()
                .map(SymbolSeries::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SecurityRiskResponseDto> securities = new ArrayList<>();
        for (SymbolSeries symbol : series.values()) {
            List<BigDecimal> returns = List.copyOf(symbol.returns().values());
            securities.add(new SecurityRiskResponseDto(
                    symbol.holding().symbol(),
                    symbol.holding().name(),
                    weight(symbol.marketValue(), totalValue),
                    asPercent(riskService.annualizedReturn(returns)),
                    asPercent(riskService.annualizedVolatility(returns)),
                    asRatio(riskService.sharpeRatio(returns, RISK_FREE_RATE)),
                    asRatio(beta(symbol.returns(), benchmarkReturns)),
                    asPercent(riskService.maxDrawdown(List.copyOf(symbol.closes().values()))),
                    asPercent(riskService.valueAtRisk95(returns))));
        }

        NavigableMap<LocalDate, BigDecimal> returnsByDate = weightedReturns(series.values(), totalValue);
        List<BigDecimal> portfolioReturns = List.copyOf(returnsByDate.values());
        if (portfolioReturns.isEmpty()) {
            return new RiskAnalysisResponseDto(
                    portfolio.getId(), portfolio.getName(), portfolio.getBaseCurrency(), from, to, benchmarkSymbol,
                    benchmarkReturn, benchmarkVolatility, 0, asPercent(RISK_FREE_RATE),
                    null, null, null, null, null, null, null, securities, excluded);
        }

        BigDecimal volatility = riskService.annualizedVolatility(portfolioReturns);
        return new RiskAnalysisResponseDto(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getBaseCurrency(),
                from,
                to,
                benchmarkSymbol,
                benchmarkReturn,
                benchmarkVolatility,
                portfolioReturns.size(),
                asPercent(RISK_FREE_RATE),
                asPercent(riskService.annualizedReturn(portfolioReturns)),
                asPercent(volatility),
                asRatio(riskService.sharpeRatio(portfolioReturns, RISK_FREE_RATE)),
                asRatio(beta(returnsByDate, benchmarkReturns)),
                asPercent(riskService.maxDrawdown(indexSeries(portfolioReturns))),
                asPercent(riskService.valueAtRisk95(portfolioReturns)),
                asPercent(diversificationBenefit(series.values(), totalValue, volatility)),
                securities,
                excluded);
    }

    /**
     * Bestaende zu Symbolen zusammengefasst, weil dasselbe Wertpapier auf mehreren Konten desselben
     * Portfolios liegen kann und zweimal derselbe Kursverlauf zwei Eintraege mit identischen Kennzahlen
     * ergaebe. Vollstaendig verkaufte Positionen (Menge 0) bleiben weg: sie tragen kein Risiko und
     * gehoeren auch nicht in {@code excluded}, weil an ihnen nichts fehlt.
     */
    private List<Holding> holdings(Long portfolioId, String username) {
        Map<String, Holding> bySymbol = new LinkedHashMap<>();
        for (Position position : positionService.listForPortfolio(portfolioId, username)) {
            BigDecimal quantity = position.getTotalQuantity();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Security security = position.getSecurity();
            Holding existing = bySymbol.get(security.getSymbol());
            BigDecimal total = existing == null ? quantity : existing.quantity().add(quantity);
            bySymbol.put(
                    security.getSymbol(),
                    new Holding(security.getSymbol(), security.getName(), security.getTradingCurrency(), total));
        }
        return List.copyOf(bySymbol.values());
    }

    /**
     * Kursverlauf eines Symbols als sortierte Reihe. Nicht positive Kurse fliegen raus, weil eine 0
     * eine Division bei der Renditeberechnung sprengen und ein negativer Kurs eine Fehlmeldung der
     * Quelle waere; ein Datum bleibt nur mit dem letzten dafuer gelieferten Kurs stehen.
     */
    private NavigableMap<LocalDate, BigDecimal> closes(String symbol, LocalDate from, LocalDate to) {
        Optional<List<HistoricalPrice>> history = marketDataProvider.getHistorical(symbol, from, to, Interval.DAILY);
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (HistoricalPrice price : history.orElseGet(List::of)) {
            if (price.date() == null || price.close() == null || price.close().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (price.date().isBefore(from) || price.date().isAfter(to)) {
                continue;
            }
            closes.put(price.date(), price.close());
        }
        return closes;
    }

    private NavigableMap<LocalDate, BigDecimal> dailyReturns(NavigableMap<LocalDate, BigDecimal> closes) {
        NavigableMap<LocalDate, BigDecimal> returns = new TreeMap<>();
        BigDecimal previous = null;
        for (Map.Entry<LocalDate, BigDecimal> entry : closes.entrySet()) {
            if (previous != null) {
                returns.put(
                        entry.getKey(),
                        entry.getValue().divide(previous, INTERNAL_SCALE, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
            previous = entry.getValue();
        }
        return returns;
    }

    /**
     * Renditereihe der Benchmark oder {@code null}, wenn sie fehlt. Im zweiten Fall steht die
     * Benchmark selbst in {@code excluded}: sonst zeigte die Oberflaeche ein leeres Beta ohne Grund,
     * und ein fehlender Referenzkurs sieht dort aus wie ein nicht berechenbares Beta des Portfolios.
     */
    private NavigableMap<LocalDate, BigDecimal> benchmarkReturns(
            String symbol, LocalDate from, LocalDate to, List<RiskExclusionDto> excluded) {
        NavigableMap<LocalDate, BigDecimal> closes = closes(symbol, from, to);
        if (closes.isEmpty()) {
            excluded.add(new RiskExclusionDto(symbol, NO_PRICE_HISTORY));
            return null;
        }
        if (closes.size() - 1 < MIN_OBSERVATIONS) {
            excluded.add(new RiskExclusionDto(symbol, TOO_FEW_OBSERVATIONS));
            return null;
        }
        return dailyReturns(closes);
    }

    /**
     * Marktwert der Position in der Waehrung des Portfolios, aus dem letzten vorliegenden Kurs.
     * {@code null} heisst "kein Wechselkurs vorhanden" - dann ist das Gewicht unbekannt, und ein
     * geschaetztes Gewicht (im Original der Faktor 1.0) waere schlimmer als eine sichtbare Luecke.
     */
    private BigDecimal marketValue(
            Holding holding, NavigableMap<LocalDate, BigDecimal> closes, String portfolioCurrency) {
        Map.Entry<LocalDate, BigDecimal> last = closes.lastEntry();
        BigDecimal valueInTradingCurrency = holding.quantity().multiply(last.getValue());
        try {
            return fxConversionService.convert(
                    valueInTradingCurrency, holding.currency(), portfolioCurrency, last.getKey());
        } catch (FxRateNotAvailableException noRate) {
            return null;
        }
    }

    private BigDecimal weight(BigDecimal marketValue, BigDecimal totalValue) {
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return asPercent(marketValue.divide(totalValue, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Tagesrenditen des Portfolios als mit den Marktwerten gewichtete Summe der Einzelrenditen, ueber
     * die Schnittmenge der Handelstage aller Wertpapiere. Ein Tag, an dem eine der Boersen zu war,
     * faellt damit fuer alle weg - das ist der Preis dafuer, keine Renditen aus verschiedenen Tagen
     * miteinander zu verrechnen. Leer, wenn es keine gemeinsamen Tage oder keinen Gesamtwert gibt.
     *
     * <p>Nach Datum und nicht als reine Liste, weil das Beta die Reihe noch einmal auf die Handelstage
     * der Benchmark ausrichten muss.</p>
     */
    private NavigableMap<LocalDate, BigDecimal> weightedReturns(
            Iterable<SymbolSeries> series, BigDecimal totalValue) {
        NavigableMap<LocalDate, BigDecimal> returns = new TreeMap<>();
        if (totalValue.compareTo(BigDecimal.ZERO) <= 0) {
            return returns;
        }
        TreeSet<LocalDate> common = null;
        for (SymbolSeries symbol : series) {
            if (common == null) {
                common = new TreeSet<>(symbol.returns().keySet());
            } else {
                common.retainAll(symbol.returns().keySet());
            }
        }
        if (common == null) {
            return returns;
        }
        for (LocalDate date : common) {
            BigDecimal sum = BigDecimal.ZERO;
            for (SymbolSeries symbol : series) {
                sum = sum.add(symbol.returns().get(date).multiply(fraction(symbol.marketValue(), totalValue)));
            }
            returns.put(date, sum);
        }
        return returns;
    }

    /**
     * Beta ueber die gemeinsamen Handelstage von Reihe und Benchmark. {@code null}, wenn die Benchmark
     * fehlt, zu wenige Tage uebereinstimmen oder die Benchmark im Zeitraum keine Varianz hat - kein
     * Ersatzwert 1.0 wie im Original, weil ein erfundenes Beta von einem berechneten nicht zu
     * unterscheiden waere.
     */
    private BigDecimal beta(
            NavigableMap<LocalDate, BigDecimal> returns, NavigableMap<LocalDate, BigDecimal> benchmarkReturns) {
        if (benchmarkReturns == null) {
            return null;
        }
        List<BigDecimal> own = new ArrayList<>();
        List<BigDecimal> benchmark = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : returns.entrySet()) {
            BigDecimal benchmarkReturn = benchmarkReturns.get(entry.getKey());
            if (benchmarkReturn != null) {
                own.add(entry.getValue());
                benchmark.add(benchmarkReturn);
            }
        }
        if (own.size() < MIN_OBSERVATIONS) {
            return null;
        }
        try {
            return riskService.beta(own, benchmark);
        } catch (IllegalArgumentException notDefined) {
            return null;
        }
    }

    /**
     * Diversifikationsgewinn in Prozentpunkten: um wie viel die Volatilitaet des Portfolios unter der
     * gewichteten Summe der Einzelvolatilitaeten liegt. Bei einem einzelnen Wertpapier ist die Frage
     * nicht gestellt, deshalb {@code null} und nicht 0.
     */
    private BigDecimal diversificationBenefit(
            Iterable<SymbolSeries> series, BigDecimal totalValue, BigDecimal portfolioVolatility) {
        int count = 0;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (SymbolSeries symbol : series) {
            count++;
            BigDecimal volatility = riskService.annualizedVolatility(List.copyOf(symbol.returns().values()));
            weightedSum = weightedSum.add(volatility.multiply(fraction(symbol.marketValue(), totalValue)));
        }
        if (count < 2) {
            return null;
        }
        return weightedSum.subtract(portfolioVolatility);
    }

    /**
     * Wertreihe mit Basis 100 aus verketteten Tagesrenditen, damit
     * {@link RiskService#maxDrawdown(List)} auch fuer das Portfolio eine Reihe bekommt, obwohl es
     * keinen eigenen Kursverlauf hat.
     */
    private List<BigDecimal> indexSeries(List<BigDecimal> returns) {
        List<BigDecimal> values = new ArrayList<>(returns.size() + 1);
        BigDecimal value = HUNDRED;
        values.add(value);
        for (BigDecimal dailyReturn : returns) {
            value = value.multiply(BigDecimal.ONE.add(dailyReturn));
            values.add(value);
        }
        return values;
    }

    private BigDecimal fraction(BigDecimal marketValue, BigDecimal totalValue) {
        return marketValue.divide(totalValue, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal asPercent(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal asRatio(BigDecimal value) {
        return value == null ? null : value.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private record Holding(String symbol, String name, String currency, BigDecimal quantity) {
    }

    private record SymbolSeries(
            Holding holding,
            NavigableMap<LocalDate, BigDecimal> closes,
            NavigableMap<LocalDate, BigDecimal> returns,
            BigDecimal marketValue) {
    }
}
