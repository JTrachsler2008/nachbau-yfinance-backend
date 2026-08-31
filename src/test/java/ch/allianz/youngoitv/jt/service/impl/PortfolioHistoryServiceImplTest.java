package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.PortfolioHistoryPointDto;
import ch.allianz.youngoitv.jt.dto.PortfolioHistoryResponseDto;
import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import ch.allianz.youngoitv.jt.service.TwrService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Die Kursreihen sind absichtlich dünn (vier Stichtage) und die Kurse absichtlich runde Zahlen: so
 * lässt sich jede erwartete Zahl von Hand nachrechnen, und ein Test, der bricht, sagt welche Annahme
 * gebrochen ist statt nur dass eine Summe anders ist.
 *
 * <p>Wo ein Cashflow im Spiel ist, steht der Kurs an diesem Tag bewusst still. Dann sind die Zahlen
 * unabhängig davon, wann innerhalb des Tages der Cashflow angenommen wird, und der Test prüft die
 * Zuordnung des Cashflows zur Periode, nicht die Konvention von {@link TwrService}.</p>
 */
@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 1);
    private static final LocalDate MAR = LocalDate.of(2024, 3, 1);
    private static final LocalDate TO = LocalDate.of(2024, 3, 31);
    private static final LocalDate BEFORE = LocalDate.of(2023, 12, 1);

    private static final Long PORTFOLIO_ID = 7L;
    private static final String USER = "anna";
    private static final String BENCHMARK = "SPY";

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private FxConversionService fxConversionService;

    private PortfolioHistoryServiceImpl service;

    /** Kursreihen je Symbol, die die Testfälle vor dem Aufruf füllen. */
    private final Map<String, Map<LocalDate, BigDecimal>> prices = new LinkedHashMap<>();

    private final List<Transaction> transactions = new ArrayList<>();

    private long nextId = 1;

    @BeforeEach
    void setUp() {
        service = new PortfolioHistoryServiceImpl(
                portfolioService, transactionService, marketDataProvider, fxConversionService, new TwrService());

        Portfolio portfolio = new Portfolio();
        portfolio.setId(PORTFOLIO_ID);
        portfolio.setBaseCurrency("CHF");
        lenient().when(portfolioService.getOwnedOrThrow(PORTFOLIO_ID, USER)).thenReturn(portfolio);
        lenient().when(transactionService.getTransactionsForPortfolio(PORTFOLIO_ID)).thenReturn(transactions);
        lenient().when(marketDataProvider.getHistorical(anyString(), any(), any(), eq(Interval.DAILY)))
                .thenAnswer(invocation -> {
                    Map<LocalDate, BigDecimal> series = prices.get(invocation.<String>getArgument(0));
                    if (series == null) {
                        return Optional.empty();
                    }
                    List<HistoricalPrice> history = series.entrySet().stream()
                            .map(entry -> new HistoricalPrice(entry.getKey(), entry.getValue()))
                            .toList();
                    return Optional.of(history);
                });
        // Standardfall: keine Fremdwährung im Spiel. Einzelne Tests überschreiben das.
        lenient().when(fxConversionService.convert(any(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.<BigDecimal>getArgument(0));
    }

    /**
     * Buy and hold: der Wert folgt dem Kurs, der Einsatz bleibt stehen, und die zeitgewichtete Rendite
     * ist die Kursentwicklung von 100 auf 120.
     */
    @Test
    void aHeldPositionYieldsTheValueSeriesAndItsReturn() {
        givenPrices("AAA", 100, 110, 110, 120);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.currency()).isEqualTo("CHF");
        assertThat(result.seriesFrom()).isEqualTo(FROM);
        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::date, PortfolioHistoryPointDto::value)
                .containsExactly(
                        tuple(FROM, new BigDecimal("1000.00")),
                        tuple(FEB, new BigDecimal("1100.00")),
                        tuple(MAR, new BigDecimal("1100.00")),
                        tuple(TO, new BigDecimal("1200.00")));
        assertThat(result.points()).allSatisfy(point ->
                assertThat(point.invested()).isEqualByComparingTo("1000.00"));
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("20.00");
        assertThat(result.excluded()).isEmpty();
    }

    /**
     * Der Kern der zeitgewichteten Rendite: der Wert wächst von 1000 auf 2400, aber 1000 davon sind
     * eingezahlt und nicht verdient. Verdient sind die 20 % Kursanstieg im März.
     */
    @Test
    void aPurchaseDuringThePeriodRaisesTheValueButNotTheReturn() {
        givenPrices("AAA", 100, 100, 100, 120);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 100);
        buy("AAA", "CHF", FEB, 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::value)
                .containsExactly(
                        new BigDecimal("1000.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("2400.00"));
        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::invested)
                .containsExactly(
                        new BigDecimal("1000.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("2000.00"));
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("20.00");
        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::index)
                .containsExactly(
                        new BigDecimal("100.0000"),
                        new BigDecimal("100.0000"),
                        new BigDecimal("100.0000"),
                        new BigDecimal("120.0000"));
    }

    /** Ein Verkauf senkt den Wert und den Einsatz, ohne die Rendite zu berühren. */
    @Test
    void aSaleRemovesThePositionFromTheSeries() {
        givenPrices("AAA", 100, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 100);
        sell("AAA", "CHF", FEB, 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::value)
                .containsExactly(
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"));
        assertThat(result.points().get(3).invested()).isEqualByComparingTo("0.00");
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("0.00");
    }

    /** Ein Split verdreifacht die Stückzahl und darf den Wert deshalb nicht verändern. */
    @Test
    void aSplitLeavesTheValueUnchanged() {
        givenPrices("AAA", 300, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 300);
        split("AAA", FEB, 3);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::value)
                .containsExactly(
                        new BigDecimal("3000.00"),
                        new BigDecimal("3000.00"),
                        new BigDecimal("3000.00"),
                        new BigDecimal("3000.00"));
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("0.00");
    }

    /** Fremdwährung: 10 Stück zu 100 USD sind bei 0.90 genau 900 CHF. */
    @Test
    void aForeignCurrencyPositionIsConvertedAtTheRateOfTheDay() {
        givenPrices("AAA", 100, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "USD", BEFORE, 10, 100);
        when(fxConversionService.getRate(eq("USD"), eq("CHF"), any())).thenReturn(new BigDecimal("0.90"));

        PortfolioHistoryResponseDto result = history();

        assertThat(result.points()).allSatisfy(point ->
                assertThat(point.value()).isEqualByComparingTo("900.00"));
    }

    /**
     * Ein Wertpapier ohne jede Kurshistorie fliegt aus der Bewertung und wird genannt - wie beim
     * Marktwert. Die Linie zeigt dann den Rest, aber nicht schweigend.
     */
    @Test
    void aSecurityWithoutAnyHistoryIsExcludedAndNamed() {
        givenPrices("AAA", 100, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 100);
        buy("ZZZ", "CHF", BEFORE, 5, 50);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.excluded())
                .extracting("symbol", "reason")
                .containsExactly(tuple("ZZZ", PortfolioHistoryServiceImpl.NO_PRICE_HISTORY));
        assertThat(result.points()).allSatisfy(point ->
                assertThat(point.value()).isEqualByComparingTo("1000.00"));
    }

    /**
     * Andere Lage: die Historie existiert, beginnt aber später. Dann ist der Wert an den früheren Tagen
     * nicht bekannt, und die Reihe beginnt später statt eine zu kleine Summe zu zeigen. Kein
     * Ausschluss - das Wertpapier ist ja bewertbar, nur nicht immer.
     */
    @Test
    void aHistoryStartingLateShiftsTheSeriesStartInsteadOfUnderstatingTheValue() {
        givenPrices("AAA", 100, 100, 100, 110);
        prices.put("BBB", new LinkedHashMap<>(Map.of(MAR, new BigDecimal("50"), TO, new BigDecimal("55"))));
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE, 10, 100);
        buy("BBB", "CHF", BEFORE, 10, 50);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.seriesFrom()).isEqualTo(MAR);
        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::date, PortfolioHistoryPointDto::value)
                .containsExactly(tuple(MAR, new BigDecimal("1500.00")), tuple(TO, new BigDecimal("1650.00")));
        assertThat(result.excluded()).isEmpty();
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("10.00");
    }

    /** Ohne Wechselkurs am Anfang beginnt die Reihe später, und der Grund steht dabei. */
    @Test
    void aMissingFxRateDelaysTheSeriesStartAndIsReported() {
        givenPrices("AAA", 100, 100, 100, 110);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "USD", BEFORE, 10, 100);
        when(fxConversionService.getRate(eq("USD"), eq("CHF"), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(2);
            if (date.isBefore(MAR)) {
                throw new FxRateNotAvailableException("no USD/CHF rate on or before " + date);
            }
            return new BigDecimal("0.90");
        });

        PortfolioHistoryResponseDto result = history();

        assertThat(result.seriesFrom()).isEqualTo(MAR);
        assertThat(result.excluded())
                .extracting("symbol", "reason")
                .containsExactly(tuple("AAA", PortfolioHistoryServiceImpl.NO_FX_RATE));
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0).value()).isEqualByComparingTo("900.00");
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("10.00");
    }

    /**
     * Ein leeres Portfolio hat keine Rendite. Eine 0 % stünde sonst neben einer Linie auf 0 und sähe aus
     * wie ein Jahr ohne Gewinn statt wie ein Jahr ohne Anlage.
     */
    @Test
    void anEmptyPortfolioHasNoReturnAndNoIndex() {
        givenPrices(BENCHMARK, 400, 400, 400, 440);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.timeWeightedReturn()).isNull();
        assertThat(result.points()).isNotEmpty();
        assertThat(result.points()).allSatisfy(point -> {
            assertThat(point.value()).isEqualByComparingTo("0.00");
            assertThat(point.index()).isNull();
        });
        // Die Vergleichslinie bleibt: sie hängt nicht am Bestand.
        assertThat(result.benchmarkReturn()).isEqualByComparingTo("10.00");
    }

    /** Die Benchmark wird auf denselben Startpunkt normiert, sonst vergleicht die Linie nichts. */
    @Test
    void theBenchmarkIsNormalizedToTheStartOfTheSeries() {
        givenPrices("AAA", 100, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 420, 420, 440);
        buy("AAA", "CHF", BEFORE, 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.points())
                .extracting(PortfolioHistoryPointDto::benchmarkIndex)
                .containsExactly(
                        new BigDecimal("100.0000"),
                        new BigDecimal("105.0000"),
                        new BigDecimal("105.0000"),
                        new BigDecimal("110.0000"));
        assertThat(result.benchmarkReturn()).isEqualByComparingTo("10.00");
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("0.00");
    }

    /** Ohne Benchmarkhistorie bleibt die Vergleichslinie leer, statt den Rest der Antwort zu verhindern. */
    @Test
    void aBenchmarkWithoutHistoryLeavesTheComparisonEmpty() {
        givenPrices("AAA", 100, 100, 100, 110);
        buy("AAA", "CHF", BEFORE, 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.excluded())
                .extracting("symbol", "reason")
                .containsExactly(tuple(BENCHMARK, PortfolioHistoryServiceImpl.NO_PRICE_HISTORY));
        assertThat(result.benchmarkReturn()).isNull();
        assertThat(result.points()).allSatisfy(point -> assertThat(point.benchmarkIndex()).isNull());
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("10.00");
    }

    /**
     * Ein längst verkauftes Wertpapier taucht nicht mehr auf: es kostet keinen Kursabruf und stünde sonst
     * bei fehlender Historie als Ausschluss in einer Antwort, mit der es nichts zu tun hat.
     */
    @Test
    void aPositionSoldBeforeThePeriodIsIgnored() {
        givenPrices("AAA", 100, 100, 100, 100);
        givenPrices(BENCHMARK, 400, 400, 400, 400);
        buy("AAA", "CHF", BEFORE.minusMonths(2), 10, 100);
        sell("AAA", "CHF", BEFORE.minusMonths(1), 10, 100);

        PortfolioHistoryResponseDto result = history();

        assertThat(result.excluded()).extracting("symbol").doesNotContain("AAA");
        assertThat(result.points()).allSatisfy(point -> assertThat(point.value()).isEqualByComparingTo("0.00"));
    }

    /**
     * Zehn Jahre Tagesraster: die Antwort bleibt bei {@link PortfolioHistoryServiceImpl#MAX_POINTS}, und
     * der letzte Tag ist trotz Ausdünnung dabei - er trägt die Endaussage der Linie.
     */
    @Test
    void aLongSeriesIsThinnedButKeepsItsEnds() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2022, 12, 31);
        Map<LocalDate, BigDecimal> daily = new LinkedHashMap<>();
        for (LocalDate date = start.minusDays(20); !date.isAfter(end); date = date.plusDays(1)) {
            daily.put(date, new BigDecimal("100"));
        }
        prices.put("AAA", daily);
        prices.put(BENCHMARK, daily);
        buy("AAA", "CHF", start.minusYears(1), 10, 100);

        PortfolioHistoryResponseDto result =
                service.history(PORTFOLIO_ID, USER, start, end, BENCHMARK);

        assertThat(result.points().size()).isLessThanOrEqualTo(PortfolioHistoryServiceImpl.MAX_POINTS + 1);
        assertThat(result.points().get(0).date()).isEqualTo(start);
        assertThat(result.points().get(result.points().size() - 1).date()).isEqualTo(end);
        assertThat(result.timeWeightedReturn()).isEqualByComparingTo("0.00");
    }

    private PortfolioHistoryResponseDto history() {
        return service.history(PORTFOLIO_ID, USER, FROM, TO, BENCHMARK);
    }

    /** Kurse an den vier Stichtagen des Testzeitraums. */
    private void givenPrices(String symbol, int atFrom, int atFeb, int atMar, int atTo) {
        Map<LocalDate, BigDecimal> series = new LinkedHashMap<>();
        series.put(FROM, BigDecimal.valueOf(atFrom));
        series.put(FEB, BigDecimal.valueOf(atFeb));
        series.put(MAR, BigDecimal.valueOf(atMar));
        series.put(TO, BigDecimal.valueOf(atTo));
        prices.put(symbol, series);
    }

    private void buy(String symbol, String currency, LocalDate date, int quantity, int price) {
        transactions.add(transaction(TransactionType.BUY, symbol, currency, date, quantity, price));
    }

    private void sell(String symbol, String currency, LocalDate date, int quantity, int price) {
        transactions.add(transaction(TransactionType.SELL, symbol, currency, date, quantity, price));
    }

    private void split(String symbol, LocalDate date, int ratio) {
        Transaction transaction = transaction(TransactionType.SPLIT, symbol, "CHF", date, 0, 0);
        transaction.setSplitRatio(BigDecimal.valueOf(ratio));
        transactions.add(transaction);
    }

    /**
     * Alle Buchungen liegen auf einem Konto: die Trennung nach Konten hat für die Bewertung keine
     * Wirkung, und ein zweites Konto würde die Erwartungswerte nur verschieben, ohne etwas zu zeigen.
     */
    private Transaction transaction(
            TransactionType type, String symbol, String currency, LocalDate date, int quantity, int price) {
        Account account = new Account();
        account.setId(1L);

        Security security = new Security();
        security.setId(symbolId(symbol));
        security.setSymbol(symbol);
        security.setTradingCurrency(currency);

        Transaction transaction = new Transaction();
        transaction.setId(nextId++);
        transaction.setAccount(account);
        transaction.setSecurity(security);
        transaction.setTransactionType(type);
        transaction.setQuantity(BigDecimal.valueOf(quantity));
        transaction.setPrice(BigDecimal.valueOf(price));
        transaction.setTransactionCurrency(currency);
        transaction.setTransactionDate(date);
        return transaction;
    }

    /** Stabile Kennung je Symbol, damit zwei Buchungen desselben Titels dieselbe Position treffen. */
    private long symbolId(String symbol) {
        return symbol.hashCode() & 0xFFFF;
    }
}
