package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.PortfolioHistoryPointDto;
import ch.allianz.youngoitv.jt.dto.PortfolioHistoryResponseDto;
import ch.allianz.youngoitv.jt.dto.RiskExclusionDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.service.PortfolioHistoryService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import ch.allianz.youngoitv.jt.service.TwrService;
import ch.allianz.youngoitv.jt.service.ValuationPeriod;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Siehe {@link PortfolioHistoryService}.
 *
 * <p>Der Weg in vier Schritten: aus der Transaktionshistorie entsteht ein Bestandsverlauf (welche
 * Menge welchen Wertpapiers lag an welchem Tag im Depot), daraus mit den Tageskursen ein
 * Wertverlauf, daraus mit den Cashflows die Teilperioden und aus denen über {@link TwrService} die
 * zeitgewichtete Rendite. Jeder Schritt ist eine eigene Methode, weil jeder eine eigene Art von
 * Lücke hat.</p>
 *
 * <p><strong>Vorzeichen der Cashflows.</strong> Hier ist ein Kauf ein <em>Zufluss</em> ins Portfolio
 * (positiv), ein Verkauf und eine Dividende ein Abfluss (negativ). Das ist genau umgekehrt zur
 * geldgewichteten Rendite in {@code PortfolioReturnsServiceImpl}, die aus Sicht der Anlegerin
 * rechnet, wo ein Kauf Geld kostet. Beides ist richtig, aber die beiden Vorzeichenwelten dürfen sich
 * nicht berühren: {@link TwrService} addiert den Cashflow zum Anfangsvermögen, dort muss ein Kauf das
 * eingesetzte Kapital erhöhen.</p>
 *
 * <p><strong>Was als Portfoliowert gilt.</strong> Nur die Wertpapiere, nicht der Kontostand - dieselbe
 * Abgrenzung wie beim Marktwert und bei der geldgewichteten Rendite. Ein Kauf verschiebt damit Geld
 * von aussen in das Portfolio und ist deshalb überhaupt ein Cashflow; läge das Konto mit im Wert,
 * wären Käufe interne Umbuchungen und Ein-/Auszahlungen die externen Flüsse. Die gewählte Abgrenzung
 * beantwortet die Frage "wie haben sich meine Wertpapiere entwickelt", nicht "wie hat sich mein
 * Vermögen entwickelt".</p>
 *
 * <p><strong>Fehlende Daten.</strong> Ein Wertpapier ohne jede Kurshistorie fliegt aus der Bewertung
 * und erscheint in {@code excluded} - wie beim Marktwert, damit eine Summe über drei von vier
 * Positionen nicht wie eine über das ganze Portfolio aussieht. Ein Wertpapier <em>mit</em> Historie,
 * die aber erst mitten im Zeitraum beginnt, ist ein anderer Fall: dort ist der Wert an den früheren
 * Tagen unbekannt, und diese Tage bleiben ohne Wert, statt eine zu kleine Summe zu zeigen. Die Reihe
 * beginnt deshalb erst am ersten vollständig bewertbaren Tag.</p>
 *
 * <p><strong>Wo die Reihe beginnt.</strong> Nicht am angefragten ersten Tag, sondern am Tag vor dem
 * ersten Bestand ({@code seriesFrom}, Grund in {@code seriesFromReason}) - siehe
 * {@link #seriesStart(List, int)}. Ein leeres Depot ist bewertbar, aber seine 0 ist keine Rendite,
 * und eine Benchmark, die schon Monate vor dem ersten Kauf auf 100 normiert wird, stünde mit einem
 * Vorlauf neben einer TWR ohne Vorlauf.</p>
 */
@Service
public class PortfolioHistoryServiceImpl implements PortfolioHistoryService {

    /**
     * Dieselben Kennungen wie bei der Risikoanalyse, absichtlich wörtlich gleich: die Oberfläche
     * übersetzt sie an einer Stelle, und ein zweiter Code für denselben Sachverhalt wäre dort ein
     * zweiter Satz mit derselben Aussage.
     */
    static final String NO_PRICE_HISTORY = "NO_PRICE_HISTORY";

    static final String NO_FX_RATE = "NO_FX_RATE";

    /**
     * Gründe für einen späteren Beginn der Reihe. Nicht dieselben Kennungen wie oben, weil sie eine
     * andere Frage beantworten: dort geht es um ein einzelnes Wertpapier, hier um den Anfang der
     * ganzen Linie. {@code NOT_INVESTED} ist dabei kein Mangel, sondern die Vorgeschichte eines
     * Depots, das im gewählten Zeitraum erst später gekauft hat.
     */
    static final String NOT_INVESTED = "NOT_INVESTED";

    static final String MISSING_DATA = "MISSING_DATA";

    /**
     * Kalendertage, die vor dem Zeitraum zusätzlich geladen werden. Der erste Tag der Reihe ist oft
     * ein Wochenende oder ein Feiertag; ohne diesen Vorlauf gäbe es für ihn keinen Kurs und die Reihe
     * begänne ohne Grund später. Vierzehn Tage überbrücken auch eine längere Börsenpause.
     */
    private static final int ANCHOR_DAYS = 14;

    /**
     * Höchstzahl der zurückgelieferten Punkte. Zehn Jahre Tagesraster sind rund 2500 Punkte, die
     * weder ein Diagramm auflösen kann noch eine Antwort tragen soll. Ausgedünnt wird erst am Ende:
     * Bewertung und Rendite rechnen auf dem vollen Tagesraster, nur die Anzeige wird gröber.
     */
    static final int MAX_POINTS = 400;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int INDEX_SCALE = 4;
    private static final int RESULT_SCALE = 2;

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final MarketDataProvider marketDataProvider;
    private final FxConversionService fxConversionService;
    private final TwrService twrService;

    public PortfolioHistoryServiceImpl(
            PortfolioService portfolioService,
            TransactionService transactionService,
            MarketDataProvider marketDataProvider,
            FxConversionService fxConversionService,
            TwrService twrService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.marketDataProvider = marketDataProvider;
        this.fxConversionService = fxConversionService;
        this.twrService = twrService;
    }

    @Override
    public PortfolioHistoryResponseDto history(
            Long portfolioId, String username, LocalDate from, LocalDate to, String benchmarkSymbol) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);
        String baseCurrency = portfolio.getBaseCurrency();
        List<Transaction> transactions = transactionService.getTransactionsForPortfolio(portfolioId);

        Bestandsverlauf bestand = bestandsverlauf(transactions);
        NavigableMap<LocalDate, BigDecimal> cashFlows = cashFlows(transactions, baseCurrency);

        List<RiskExclusionDto> excluded = new ArrayList<>();
        Map<String, NavigableMap<LocalDate, BigDecimal>> closesBySymbol = new LinkedHashMap<>();
        for (String symbol : bestand.symbolsHeldBetween(from, to)) {
            NavigableMap<LocalDate, BigDecimal> closes = closes(symbol, from.minusDays(ANCHOR_DAYS), to);
            if (closes.isEmpty()) {
                excluded.add(new RiskExclusionDto(symbol, NO_PRICE_HISTORY));
                continue;
            }
            closesBySymbol.put(symbol, closes);
        }

        NavigableMap<LocalDate, BigDecimal> benchmarkCloses =
                closes(benchmarkSymbol, from.minusDays(ANCHOR_DAYS), to);
        if (benchmarkCloses.isEmpty()) {
            excluded.add(new RiskExclusionDto(benchmarkSymbol, NO_PRICE_HISTORY));
        }

        List<LocalDate> grid = grid(from, to, closesBySymbol.values(), benchmarkCloses, cashFlows);

        Set<String> withoutFxRate = new LinkedHashSet<>();
        Map<String, BigDecimal> rateCache = new HashMap<>();
        List<BigDecimal> values = new ArrayList<>(grid.size());
        for (LocalDate date : grid) {
            values.add(valueAt(date, bestand, closesBySymbol, baseCurrency, rateCache, withoutFxRate));
        }
        withoutFxRate.forEach(symbol -> excluded.add(new RiskExclusionDto(symbol, NO_FX_RATE)));

        int firstValued = indexOfFirstValue(values);
        if (firstValued < 0) {
            // Kein einziger bewertbarer Tag: der Grund steht trotzdem dabei, auch wenn es keine Reihe
            // gibt, deren Anfang er erklären könnte - die Ursache nennt zusätzlich `excluded`.
            return new PortfolioHistoryResponseDto(
                    portfolio.getId(), baseCurrency, from, to, null, MISSING_DATA, benchmarkSymbol,
                    null, null, List.of(), excluded);
        }
        int start = seriesStart(values, firstValued);

        List<LocalDate> dates = grid.subList(start, grid.size());
        List<BigDecimal> series = values.subList(start, values.size());
        // Ab der ersten Lücke ist die Kette unterbrochen: über sie hinweg zu verketten würde die
        // Rendite des fehlenden Abschnitts stillschweigend als 0 behandeln.
        int chainEnd = indexOfFirstGap(series);

        List<ValuationPeriod> periods = new ArrayList<>();
        for (int i = 1; i < chainEnd; i++) {
            periods.add(new ValuationPeriod(
                    series.get(i - 1), sumBetween(cashFlows, dates.get(i - 1), dates.get(i)), series.get(i)));
        }
        List<BigDecimal> factors = twrService.chain(periods);

        // Ohne je investiertes Kapital hat eine zeitgewichtete Rendite keine Bedeutung: jede Periode
        // hätte einen Nenner von 0, und die 0 am Ende wäre nicht "keine Wertänderung", sondern
        // "keine Frage gestellt".
        boolean everInvested = series.stream().anyMatch(value -> value != null && value.signum() > 0);
        boolean chainComplete = chainEnd == series.size() && !periods.isEmpty() && everInvested;

        List<BigDecimal> benchmarkIndex = benchmarkIndex(benchmarkCloses, dates);
        List<PortfolioHistoryPointDto> points = new ArrayList<>(dates.size());
        for (int i = 0; i < dates.size(); i++) {
            points.add(new PortfolioHistoryPointDto(
                    dates.get(i),
                    money(series.get(i)),
                    money(sumUpTo(cashFlows, dates.get(i))),
                    everInvested ? indexAt(factors, i, chainEnd) : null,
                    benchmarkIndex.get(i)));
        }

        return new PortfolioHistoryResponseDto(
                portfolio.getId(),
                baseCurrency,
                from,
                to,
                dates.get(0),
                seriesFromReason(values, start),
                benchmarkSymbol,
                chainComplete ? asPercent(factors.get(factors.size() - 1).subtract(BigDecimal.ONE)) : null,
                growth(benchmarkIndex),
                thin(points),
                excluded);
    }

    /**
     * Bestand je Wertpapier nach jedem Buchungstag.
     *
     * <p>Die Mengenwirkung je Buchungsart ist dieselbe wie in {@code TransactionServiceImpl}: BUY
     * addiert, SELL subtrahiert, SPLIT skaliert, ACQUISITION/MERGER ersetzt, DIVIDEND lässt den
     * Bestand unberührt. Bewusst noch einmal hier und nicht aus dem gespeicherten
     * {@code positions}-Datensatz gelesen: der kennt nur den Stand von heute, und genau der
     * Unterschied zwischen damals und heute ist der Zweck dieser Reihe.</p>
     *
     * <p>Geführt wird je Konto und Wertpapier, weil dasselbe Wertpapier auf zwei Konten desselben
     * Portfolios liegen kann und ein SPLIT dann nur die eine Position skaliert. Für die Bewertung
     * werden die Mengen anschliessend je Symbol zusammengefasst.</p>
     */
    private Bestandsverlauf bestandsverlauf(List<Transaction> transactions) {
        Map<PositionKey, BigDecimal> running = new LinkedHashMap<>();
        Map<PositionKey, String> symbolOf = new LinkedHashMap<>();
        Map<String, String> currencyBySymbol = new LinkedHashMap<>();
        NavigableMap<LocalDate, Map<String, BigDecimal>> snapshots = new TreeMap<>();

        for (Transaction transaction : transactions) {
            Security security = transaction.getSecurity();
            PositionKey key = new PositionKey(transaction.getAccount().getId(), security.getId());
            symbolOf.put(key, security.getSymbol());
            currencyBySymbol.putIfAbsent(security.getSymbol(), security.getTradingCurrency());
            running.put(key, nextQuantity(running.getOrDefault(key, BigDecimal.ZERO), transaction));
            // Derselbe Tag mehrfach: der letzte Stand des Tages bleibt stehen. Der Wert eines Tages
            // ist sein Schlusskurs, also gehört auch der Bestand nach Handelsschluss dazu.
            snapshots.put(transaction.getTransactionDate(), aggregateBySymbol(running, symbolOf));
        }
        return new Bestandsverlauf(snapshots, currencyBySymbol);
    }

    private static BigDecimal nextQuantity(BigDecimal current, Transaction transaction) {
        BigDecimal quantity = transaction.getQuantity() == null ? BigDecimal.ZERO : transaction.getQuantity();
        return switch (transaction.getTransactionType()) {
            case BUY -> current.add(quantity);
            // Eine Rückzahlung nimmt den Bestand weg wie ein Verkauf: nach Fälligkeit liegt das Papier
            // nicht mehr im Depot und darf ab diesem Tag nicht mehr mitbewertet werden.
            case SELL, REDEMPTION -> current.subtract(quantity);
            // Eine SPLIT-Buchung ohne Ratio lehnt der Transaktionsdienst ab; sollte eine alte Zeile
            // ohne dastehen, ist "Bestand unverändert" die einzige Annahme, die nichts erfindet.
            case SPLIT -> transaction.getSplitRatio() == null
                    ? current
                    : current.multiply(transaction.getSplitRatio());
            case ACQUISITION, MERGER -> quantity;
            case DIVIDEND, COUPON -> current;
        };
    }

    /** Mengen je Symbol, leere und negative Bestände weg: bewertbar ist nur, was tatsächlich liegt. */
    private static Map<String, BigDecimal> aggregateBySymbol(
            Map<PositionKey, BigDecimal> running, Map<PositionKey, String> symbolOf) {
        Map<String, BigDecimal> total = new LinkedHashMap<>();
        running.forEach((key, quantity) -> {
            if (quantity.signum() > 0) {
                total.merge(symbolOf.get(key), quantity, BigDecimal::add);
            }
        });
        return total;
    }

    /**
     * Externe Cashflows je Tag, in der Basiswährung, Zufluss ins Portfolio positiv.
     *
     * <p>Die Beträge sind dieselben, die {@code TransactionServiceImpl} dem Konto belastet oder
     * gutschreibt: ein Kauf kostet {@code price*quantity + fee + tax}, ein Verkauf und eine
     * Rückzahlung bringen {@code price*quantity - fee - tax}, ein Coupon ebenso, eine Dividende
     * {@code price*quantity}. SPLIT, ACQUISITION und MERGER rühren den Kontostand nicht an und sind
     * deshalb keine Cashflows.</p>
     *
     * <p>Die Umrechnung darf hier werfen, ohne dass es einen Auffangzweig braucht: eine Buchung wird
     * nur angelegt, wenn zu ihrem Datum ein Kurs in die Basiswährung des Portfolios vorliegt (siehe
     * {@code TransactionServiceImpl.resolveFxRate}). Für eine gespeicherte Zeile ist der Kurs also
     * vorhanden.</p>
     */
    private NavigableMap<LocalDate, BigDecimal> cashFlows(List<Transaction> transactions, String baseCurrency) {
        NavigableMap<LocalDate, BigDecimal> flows = new TreeMap<>();
        for (Transaction transaction : transactions) {
            BigDecimal amount = externalFlow(transaction);
            if (amount == null) {
                continue;
            }
            BigDecimal converted = fxConversionService.convert(
                    amount, transaction.getTransactionCurrency(), baseCurrency, transaction.getTransactionDate());
            flows.merge(transaction.getTransactionDate(), converted, BigDecimal::add);
        }
        return flows;
    }

    private static BigDecimal externalFlow(Transaction transaction) {
        BigDecimal quantity = transaction.getQuantity();
        BigDecimal price = transaction.getPrice();
        if (quantity == null || price == null) {
            return null;
        }
        BigDecimal fee = orZero(transaction.getFee());
        BigDecimal tax = orZero(transaction.getTax());
        return switch (transaction.getTransactionType()) {
            case BUY -> price.multiply(quantity).add(fee).add(tax);
            // Abfluss aus dem Wertpapierbestand, deshalb negativ: Verkauf und Rückzahlung machen aus
            // Bestand Cash, der Coupon nimmt einen Ertrag heraus, den der Depotwert nie enthielt.
            case SELL, REDEMPTION, COUPON -> price.multiply(quantity).subtract(fee).subtract(tax).negate();
            case DIVIDEND -> price.multiply(quantity).negate();
            case SPLIT, ACQUISITION, MERGER -> null;
        };
    }

    /**
     * Kursverlauf eines Symbols als sortierte Reihe. Nicht positive Kurse fliegen raus, weil eine 0
     * eine Fehlmeldung der Quelle wäre und einen Portfoliowert um den ganzen Posten senken würde.
     */
    private NavigableMap<LocalDate, BigDecimal> closes(String symbol, LocalDate start, LocalDate end) {
        Optional<List<HistoricalPrice>> history =
                marketDataProvider.getHistorical(symbol, start, end, Interval.DAILY);
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (HistoricalPrice price : history.orElseGet(List::of)) {
            if (price.date() == null || price.close() == null || price.close().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (price.date().isBefore(start) || price.date().isAfter(end)) {
                continue;
            }
            closes.put(price.date(), price.close());
        }
        return closes;
    }

    /**
     * Die Stichtage der Reihe: die Handelstage aller beteiligten Wertpapiere und der Benchmark, dazu
     * jeder Buchungstag und die beiden Ränder des Zeitraums.
     *
     * <p>Vereinigung und nicht Schnittmenge wie bei der Risikoanalyse: dort werden Tagesrenditen
     * verschiedener Titel miteinander verrechnet, und ein Tag, den nur eine Börse kennt, würde
     * Renditen aus verschiedenen Tagen paaren. Hier wird jedes Wertpapier für sich mit seinem letzten
     * bekannten Kurs bewertet, was an einem für ihn geschlossenen Tag genau richtig ist: sein Wert hat
     * sich an diesem Tag nicht geändert.</p>
     *
     * <p>Die Buchungstage müssen dabei sein, weil an ihnen der Cashflow steht. Fiele ein Kauftag aus
     * dem Raster, läge der Cashflow in derselben Periode wie die Kursbewegung mehrerer Tage, und die
     * Rendite dieser Periode wäre um den Kaufbetrag verzerrt.</p>
     */
    private List<LocalDate> grid(
            LocalDate from,
            LocalDate to,
            Collection<NavigableMap<LocalDate, BigDecimal>> closesBySymbol,
            NavigableMap<LocalDate, BigDecimal> benchmarkCloses,
            NavigableMap<LocalDate, BigDecimal> cashFlows) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.add(from);
        dates.add(to);
        for (NavigableMap<LocalDate, BigDecimal> closes : closesBySymbol) {
            dates.addAll(closes.subMap(from, false, to, true).keySet());
        }
        dates.addAll(benchmarkCloses.subMap(from, false, to, true).keySet());
        dates.addAll(cashFlows.subMap(from, false, to, true).keySet());
        return List.copyOf(dates);
    }

    /**
     * Marktwert des an diesem Tag gehaltenen Bestands in der Basiswährung, oder {@code null}, wenn
     * ein gehaltenes Wertpapier nicht bewertbar war.
     */
    private BigDecimal valueAt(
            LocalDate date,
            Bestandsverlauf bestand,
            Map<String, NavigableMap<LocalDate, BigDecimal>> closesBySymbol,
            String baseCurrency,
            Map<String, BigDecimal> rateCache,
            Set<String> withoutFxRate) {
        BigDecimal value = BigDecimal.ZERO;
        boolean complete = true;
        for (Map.Entry<String, BigDecimal> holding : bestand.holdingsAt(date).entrySet()) {
            NavigableMap<LocalDate, BigDecimal> closes = closesBySymbol.get(holding.getKey());
            if (closes == null) {
                // Ganz ohne Kurshistorie: steht schon in `excluded` und bleibt aus der Summe. Der Tag
                // gilt trotzdem als bewertet, sonst gäbe es wegen eines Symbols keine Linie.
                continue;
            }
            Map.Entry<LocalDate, BigDecimal> close = closes.floorEntry(date);
            if (close == null) {
                complete = false;
                continue;
            }
            BigDecimal rate = rate(bestand.currencyOf(holding.getKey()), baseCurrency, date, rateCache);
            if (rate == null) {
                complete = false;
                withoutFxRate.add(holding.getKey());
                continue;
            }
            value = value.add(holding.getValue().multiply(close.getValue()).multiply(rate));
        }
        // Auf Geldgenauigkeit gerundet, und zwar hier: Diagramm und Rendite sollen auf derselben Zahl
        // beruhen, nicht auf einer gerundeten und einer ungerundeten Fassung derselben Reihe.
        return complete ? value.setScale(MONEY_SCALE, RoundingMode.HALF_UP) : null;
    }

    /**
     * Wechselkurs mit Zwischenspeicher, {@code null} wenn keiner hinterlegt ist.
     *
     * <p>{@code fx_rates} ist eine von Hand gepflegte Tabelle; der Lookup schreibt den letzten
     * bekannten Kurs fort und scheitert deshalb nur für Tage <em>vor</em> dem ersten erfassten Kurs
     * eines Paars. Das ist ein Anfangsstück des Zeitraums, kein Loch in der Mitte - die Reihe beginnt
     * dann später, und {@code excluded} nennt den Grund.</p>
     *
     * <p>Der Zwischenspeicher hält auch das Fehlen fest: ohne ihn liefe für jeden der bis zu 2500
     * Tage eine eigene Abfrage in dieselbe Ausnahme.</p>
     */
    private BigDecimal rate(
            String currency, String baseCurrency, LocalDate date, Map<String, BigDecimal> rateCache) {
        if (currency.equals(baseCurrency)) {
            return BigDecimal.ONE;
        }
        String key = currency + '@' + date;
        if (rateCache.containsKey(key)) {
            return rateCache.get(key);
        }
        BigDecimal rate;
        try {
            rate = fxConversionService.getRate(currency, baseCurrency, date);
        } catch (FxRateNotAvailableException noRate) {
            rate = null;
        }
        rateCache.put(key, rate);
        return rate;
    }

    /**
     * Benchmark auf denselben Startpunkt normiert (Basis 100 am ersten Tag der Reihe), sonst nur
     * lauter {@code null}: eine Vergleichslinie, die anders startet, vergleicht nichts.
     */
    private List<BigDecimal> benchmarkIndex(
            NavigableMap<LocalDate, BigDecimal> benchmarkCloses, List<LocalDate> dates) {
        List<BigDecimal> index = new ArrayList<>(dates.size());
        Map.Entry<LocalDate, BigDecimal> base = benchmarkCloses.floorEntry(dates.get(0));
        for (LocalDate date : dates) {
            Map.Entry<LocalDate, BigDecimal> close = base == null ? null : benchmarkCloses.floorEntry(date);
            index.add(close == null
                    ? null
                    : HUNDRED.multiply(close.getValue())
                            .divide(base.getValue(), INDEX_SCALE, RoundingMode.HALF_UP));
        }
        return index;
    }

    /**
     * Erster Tag der Reihe: der Tag vor dem ersten Bestand, nicht der erste bewertbare Tag.
     *
     * <p>Ein Depot, dessen erster Kauf mitten im gewählten Zeitraum liegt, ist an den Tagen davor
     * bewertbar - es ist 0 wert. Diese Tage gehören trotzdem nicht in die Reihe: sie sind keine
     * Nullrendite, sondern keine Rendite. Die Vergleichslinie würde über sie hinweg laufen, weil die
     * Benchmark am ersten Tag der Reihe auf 100 normiert wird, und neben einer TWR stehen, die erst
     * beim Kauf beginnt. Beide Zahlen sind dann einzeln richtig und nebeneinander irreführend: ein
     * halbes Jahr Marktentwicklung gegen ein Depot, das in diesem halben Jahr gar nicht am Markt war.
     * </p>
     *
     * <p>Ein Tag ohne Bestand bleibt als Basis stehen, nicht null Tage: nur so fällt der Cashflow des
     * ersten Kaufs in die erste Teilperiode, und deren Rendite enthält den Unterschied zwischen
     * bezahltem Preis und Schlusskurs des Kauftags. Fehlt dieser Tag als Wert - etwa weil an ihm ein
     * anderes, nicht bewertbares Wertpapier lag -, beginnt die Reihe beim Bestand selbst.</p>
     *
     * <p>Ein Zeitraum ganz ohne Bestand behält den ersten bewertbaren Tag: eine Wertlinie auf 0 ist
     * die richtige Auskunft über ein leeres Depot, und Index und TWR bleiben ohnehin leer.</p>
     */
    private static int seriesStart(List<BigDecimal> values, int firstValued) {
        int firstHolding = -1;
        for (int i = firstValued; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            if (value != null && value.signum() > 0) {
                firstHolding = i;
                break;
            }
        }
        if (firstHolding <= firstValued) {
            return firstValued;
        }
        BigDecimal dayBefore = values.get(firstHolding - 1);
        return dayBefore != null && dayBefore.signum() == 0 ? firstHolding - 1 : firstHolding;
    }

    /**
     * Warum die Reihe erst später beginnt, oder {@code null}, wenn sie am angefragten Tag beginnt.
     *
     * <p>Gelesen wird an den weggelassenen Tagen selbst und nicht an den Indizes, weil beide Ursachen
     * zugleich auftreten können: ein Depot, das später kauft und dessen erster Titel obendrein noch
     * keinen Kurs hat. War einer der übersprungenen Tage nicht bewertbar, ist das die Auskunft, die
     * zählt - ein Loch in den Daten ist ein Mangel, eine leere Vorgeschichte nicht.</p>
     */
    private static String seriesFromReason(List<BigDecimal> values, int start) {
        if (start == 0) {
            return null;
        }
        for (int i = 0; i < start; i++) {
            if (values.get(i) == null) {
                return MISSING_DATA;
            }
        }
        return NOT_INVESTED;
    }

    /** Erste Stelle mit einem Wert, oder -1 wenn die Reihe an keinem Tag bewertbar war. */
    private static int indexOfFirstValue(List<BigDecimal> values) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    /** Erste Lücke nach dem Start, oder die Länge der Reihe, wenn sie lückenlos ist. */
    private static int indexOfFirstGap(List<BigDecimal> series) {
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i) == null) {
                return i;
            }
        }
        return series.size();
    }

    /**
     * Indexwert an einer Stelle der Reihe: 100 am Start, danach der Wachstumsfaktor der Kette. Ab der
     * ersten Lücke {@code null}, weil eine Kette mit einem fehlenden Glied keinen Stand hat.
     */
    private static BigDecimal indexAt(List<BigDecimal> factors, int position, int chainEnd) {
        if (position >= chainEnd) {
            return null;
        }
        if (position == 0) {
            return HUNDRED.setScale(INDEX_SCALE);
        }
        return HUNDRED.multiply(factors.get(position - 1)).setScale(INDEX_SCALE, RoundingMode.HALF_UP);
    }

    /** Summe der Cashflows in {@code (after, upTo]} - das Intervall einer Teilperiode. */
    private static BigDecimal sumBetween(
            NavigableMap<LocalDate, BigDecimal> cashFlows, LocalDate after, LocalDate upTo) {
        return cashFlows.subMap(after, false, upTo, true).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Summe aller Cashflows bis einschliesslich {@code date}, auch der vor dem Zeitraum. */
    private static BigDecimal sumUpTo(NavigableMap<LocalDate, BigDecimal> cashFlows, LocalDate date) {
        return cashFlows.headMap(date, true).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Veränderung einer auf 100 normierten Reihe in Prozent, {@code null} ohne Anfang oder Ende. */
    private static BigDecimal growth(List<BigDecimal> index) {
        BigDecimal first = index.isEmpty() ? null : index.get(0);
        BigDecimal last = index.isEmpty() ? null : index.get(index.size() - 1);
        if (first == null || last == null || first.signum() <= 0) {
            return null;
        }
        return last.subtract(first)
                .divide(first, 10, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Dünnt die Reihe auf {@link #MAX_POINTS} aus, indem sie jeden n-ten Punkt behält. Erster und
     * letzter Punkt bleiben in jedem Fall: der erste ist die Basis 100, der letzte trägt die
     * Endaussage der Linie.
     */
    private static List<PortfolioHistoryPointDto> thin(List<PortfolioHistoryPointDto> points) {
        if (points.size() <= MAX_POINTS) {
            return points;
        }
        int step = (points.size() + MAX_POINTS - 1) / MAX_POINTS;
        List<PortfolioHistoryPointDto> reduced = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            reduced.add(points.get(i));
        }
        PortfolioHistoryPointDto last = points.get(points.size() - 1);
        if (!reduced.get(reduced.size() - 1).date().equals(last.date())) {
            reduced.add(last);
        }
        return reduced;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal asPercent(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Eine Position, also ein Wertpapier auf einem Konto - die Ebene, auf der ein SPLIT wirkt. */
    private record PositionKey(Long accountId, Long securityId) {
    }

    /** Bestand je Symbol nach jedem Buchungstag, samt Handelswährung je Symbol. */
    private record Bestandsverlauf(
            NavigableMap<LocalDate, Map<String, BigDecimal>> snapshots, Map<String, String> currencyBySymbol) {

        /** Bestand an einem beliebigen Tag: der Stand nach der letzten Buchung an oder vor diesem Tag. */
        Map<String, BigDecimal> holdingsAt(LocalDate date) {
            Map.Entry<LocalDate, Map<String, BigDecimal>> entry = snapshots.floorEntry(date);
            return entry == null ? Map.of() : entry.getValue();
        }

        String currencyOf(String symbol) {
            return currencyBySymbol.get(symbol);
        }

        /**
         * Symbole, die im Zeitraum mindestens einen Tag im Depot lagen: der Bestand zu Beginn plus
         * alles, was danach dazukam. Ein längst verkauftes Wertpapier bleibt damit aussen vor und
         * kostet keinen Kursabruf.
         */
        Set<String> symbolsHeldBetween(LocalDate from, LocalDate to) {
            Set<String> symbols = new LinkedHashSet<>();
            Map.Entry<LocalDate, Map<String, BigDecimal>> atStart = snapshots.floorEntry(from);
            if (atStart != null) {
                symbols.addAll(atStart.getValue().keySet());
            }
            snapshots.subMap(from, false, to, true).values().forEach(held -> symbols.addAll(held.keySet()));
            return symbols;
        }
    }
}
