package ch.allianz.youngoitv.jt.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Kursreihen kommen aus einem Testdouble mit abwechselnden Faktoren, damit die Kennzahlen von Hand
 * nachrechenbar sind: bei einem Wechsel von +2% und -2% ist der Mittelwert exakt 0 und die
 * Tagesstandardabweichung exakt 0.02, unabhaengig von der Laenge der Reihe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RiskControllerTest {

    /** 25 Kurse ergeben 24 Tagesrenditen und liegen damit ueber der Untergrenze von 20. */
    private static final int HANDELSTAGE = 25;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        var user = userService.register(username, username + "@example.com", "password123");
        // ADMIN, damit derselbe Test-User die Wertpapier-Stammdaten anlegen darf.
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.generateToken(username);
    }

    private long jsonId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPortfolio(String token) throws Exception {
        return jsonId(mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"baseCurrency\":\"CHF\"}"))
                .andReturn());
    }

    private long createAccount(String token, long portfolioId) throws Exception {
        return jsonId(mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cash\",\"currency\":\"CHF\"}"))
                .andReturn());
    }

    private long createSecurity(String token, String symbol, String tradingCurrency) throws Exception {
        return jsonId(mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"%s","name":"%s Inc.","assetType":"STOCK","tradingCurrency":"%s",
                                 "sector":"Technology","countryCode":"CH"}
                                """.formatted(symbol, symbol, tradingCurrency)))
                .andReturn());
    }

    /** Bestand aufbauen. Der Preis wird mitgegeben, damit kein Kurs-Lookup dazwischenkommt. */
    private void buy(String token, long accountId, long securityId, String quantity) throws Exception {
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000000.00}"));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":%s,"price":100.00,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId, quantity)))
                .andExpect(status().isCreated());
    }

    /**
     * Kursreihe, die auf dem letzten Tag des Abrufzeitraums endet (gestern) und {@code tage} Tage
     * zurueckreicht, mit den Faktoren der Reihe nach im Kreis. Kalendertage und keine Handelstage: der
     * Dienst schneidet nach Datum, ein uebersprungenes Wochenende wuerde die Handrechnung nur
     * unuebersichtlicher machen.
     */
    private static List<HistoricalPrice> reihe(String startkurs, int tage, String... faktoren) {
        List<HistoricalPrice> prices = new ArrayList<>();
        BigDecimal kurs = new BigDecimal(startkurs);
        LocalDate datum = LocalDate.now().minusDays(tage);
        prices.add(new HistoricalPrice(datum, kurs));
        for (int i = 0; i < tage - 1; i++) {
            kurs = kurs.multiply(new BigDecimal(faktoren[i % faktoren.length]));
            datum = datum.plusDays(1);
            prices.add(new HistoricalPrice(datum, kurs));
        }
        return prices;
    }

    private void kurse(String symbol, List<HistoricalPrice> prices) {
        when(marketDataProvider.getHistorical(eq(symbol), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(prices));
    }

    private long portfolioMitBestand(String token, String symbol, String menge) throws Exception {
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId);
        buy(token, accountId, createSecurity(token, symbol, "CHF"), menge);
        return portfolioId;
    }

    /**
     * Handrechnung fuer AAA (+2%/-2% im Wechsel) gegen SPY (+1%/-1% im Wechsel), beide 24 Renditen:
     * Mittelwert je 0, Tagesstandardabweichung 0.02 bzw. 0.01, annualisiert 0.02*sqrt(252) = 31.75%.
     * Sharpe (0 - 0.04)/0.317490 = -0.13. AAA ist punktweise genau das Doppelte von SPY, also Beta 2.00.
     * VaR 95%: bei 24 Werten ist rank = ceil(1.2) = 2, also der zweitschlechteste Wert = -2.00%.
     * Verkettet: (1.02*0.98)^12 = 0.9996^12, auf ein Jahr 0.9996^126 = 0.95084, somit -4.92%.
     */
    @Test
    void returnsHandComputedMetricsForASinglePosition() throws Exception {
        String token = tokenFor("rita");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        kurse("SPY", reihe("100", HANDELSTAGE, "1.01", "0.99"));
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value((int) portfolioId))
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.benchmarkSymbol").value("SPY"))
                .andExpect(jsonPath("$.observations").value(24))
                .andExpect(jsonPath("$.riskFreeRate").value(4.00))
                .andExpect(jsonPath("$.volatility").value(31.75))
                .andExpect(jsonPath("$.sharpeRatio").value(-0.13))
                .andExpect(jsonPath("$.beta").value(2.00))
                .andExpect(jsonPath("$.valueAtRisk95").value(-2.00))
                .andExpect(jsonPath("$.annualizedReturn").value(-4.92))
                .andExpect(jsonPath("$.excluded.length()").value(0))
                .andExpect(jsonPath("$.securities.length()").value(1))
                .andExpect(jsonPath("$.securities[0].symbol").value("AAA"))
                .andExpect(jsonPath("$.securities[0].securityName").value("AAA Inc."))
                .andExpect(jsonPath("$.securities[0].weight").value(100.00))
                .andExpect(jsonPath("$.securities[0].volatility").value(31.75))
                .andExpect(jsonPath("$.securities[0].beta").value(2.00));
    }

    /**
     * Bei einem einzigen Wertpapier ist der Diversifikationsgewinn keine sinnvolle Frage: die
     * gewichtete Summe der Einzelvolatilitaeten ist die Portfoliovolatilitaet, die Differenz waere
     * immer 0 und sahe aus wie ein Befund.
     */
    @Test
    void diversificationBenefitIsNullForASinglePosition() throws Exception {
        String token = tokenFor("samuel");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diversificationBenefit").value(nullValue()));
    }

    /**
     * Zwei gleich grosse Bestaende mit gegenlaeufigen Kursen: die gewichtete Tagesrendite ist an jedem
     * Tag 0.5*(+1%) + 0.5*(-1%) = 0, die Volatilitaet des Portfolios also 0, waehrend jeder Titel
     * einzeln auf 0.01*sqrt(252) = 15.87% kommt. Der Diversifikationsgewinn ist damit die vollen
     * 15.87 Prozentpunkte. Die Gewichte sind exakt gleich, weil beide Reihen zwoelfmal 0.99*1.01
     * durchlaufen und deshalb auf demselben Endkurs landen.
     */
    @Test
    void weightsBySharedMarketValueAndReportsTheDiversificationBenefit() throws Exception {
        String token = tokenFor("tamara");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.01", "0.99"));
        kurse("BBB", reihe("100", HANDELSTAGE, "0.99", "1.01"));
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId);
        buy(token, accountId, createSecurity(token, "AAA", "CHF"), "10");
        buy(token, accountId, createSecurity(token, "BBB", "CHF"), "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value(24))
                .andExpect(jsonPath("$.volatility").value(0.00))
                .andExpect(jsonPath("$.annualizedReturn").value(0.00))
                .andExpect(jsonPath("$.maxDrawdown").value(0.00))
                .andExpect(jsonPath("$.diversificationBenefit").value(15.87))
                .andExpect(jsonPath("$.securities[0].weight").value(50.00))
                .andExpect(jsonPath("$.securities[1].weight").value(50.00))
                .andExpect(jsonPath("$.securities[0].volatility").value(15.87))
                .andExpect(jsonPath("$.securities[1].volatility").value(15.87));
    }

    /**
     * Reihen unterschiedlicher Laenge werden ueber die gemeinsamen Handelstage ausgerichtet, nicht vom
     * Ende her durchgezaehlt. BBB liefert nur die letzten 22 Tage, also 21 Renditen - genau die bleiben
     * uebrig, obwohl AAA 24 haette.
     */
    @Test
    void alignsSeriesOfDifferentLengthOnTheirCommonTradingDays() throws Exception {
        String token = tokenFor("urs");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.01", "0.99"));
        kurse("BBB", reihe("100", 22, "1.01", "0.99"));
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId);
        buy(token, accountId, createSecurity(token, "AAA", "CHF"), "10");
        buy(token, accountId, createSecurity(token, "BBB", "CHF"), "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value(21))
                .andExpect(jsonPath("$.securities.length()").value(2));
    }

    @Test
    void namesTheSymbolWithoutPriceHistoryInsteadOfDroppingItSilently() throws Exception {
        String token = tokenFor("vera");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        when(marketDataProvider.getHistorical(eq("BBB"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId);
        buy(token, accountId, createSecurity(token, "AAA", "CHF"), "10");
        buy(token, accountId, createSecurity(token, "BBB", "CHF"), "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securities.length()").value(1))
                .andExpect(jsonPath("$.securities[0].symbol").value("AAA"))
                // Das Gewicht bezieht sich auf die verwertbaren Titel, sonst summierten sich die
                // ausgewiesenen Gewichte nicht auf 100%.
                .andExpect(jsonPath("$.securities[0].weight").value(100.00))
                .andExpect(jsonPath("$.excluded[?(@.symbol == 'BBB')].reason").value("NO_PRICE_HISTORY"));
    }

    @Test
    void namesTheSymbolWithTooFewObservations() throws Exception {
        String token = tokenFor("werner");
        // 15 Kurse sind 14 Renditen, unter der Untergrenze von 20.
        kurse("AAA", reihe("100", 15, "1.02", "0.98"));
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securities.length()").value(0))
                .andExpect(jsonPath("$.observations").value(0))
                .andExpect(jsonPath("$.volatility").value(nullValue()))
                .andExpect(jsonPath("$.excluded[?(@.symbol == 'AAA')].reason").value("TOO_FEW_OBSERVATIONS"));
    }

    /**
     * Ohne Wechselkurs ist der Marktwert und damit das Gewicht des Titels unbekannt. Das Original
     * rechnete in diesem Fall mit dem Faktor 1.0 weiter, was einen USD-Bestand wie einen CHF-Bestand
     * gewichtete.
     */
    @Test
    void namesTheSymbolWithoutAnFxRate() throws Exception {
        String token = tokenFor("xenia");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId);
        buy(token, accountId, createSecurity(token, "AAA", "USD"), "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securities.length()").value(0))
                .andExpect(jsonPath("$.excluded[?(@.symbol == 'AAA')].reason").value("NO_FX_RATE"));
    }

    /**
     * Fehlt die Benchmark, bleibt das Beta leer und die Benchmark steht mit ihrem Grund in
     * {@code excluded}. Ohne diesen Eintrag zeigte die Oberflaeche ein leeres Beta, ohne sagen zu
     * koennen, dass nicht das Portfolio, sondern die Referenz das Problem ist.
     */
    @Test
    void leavesBetaEmptyAndNamesTheBenchmarkWhenItHasNoData() throws Exception {
        String token = tokenFor("yves");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        when(marketDataProvider.getHistorical(eq("SPY"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volatility").value(31.75))
                .andExpect(jsonPath("$.beta").value(nullValue()))
                .andExpect(jsonPath("$.securities[0].beta").value(nullValue()))
                .andExpect(jsonPath("$.excluded[?(@.symbol == 'SPY')].reason").value("NO_PRICE_HISTORY"));
    }

    /**
     * Eine Benchmark ohne Bewegung hat keine Varianz, das Beta ist dann nicht definiert. Das Original
     * lieferte hier 1.0, also den Wert "laeuft wie der Markt", der von einem gerechneten Beta nicht zu
     * unterscheiden war.
     */
    @Test
    void leavesBetaEmptyWhenTheBenchmarkNeverMoves() throws Exception {
        String token = tokenFor("zora");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        kurse("SPY", reihe("100", HANDELSTAGE, "1.00"));
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beta").value(nullValue()))
                .andExpect(jsonPath("$.excluded.length()").value(0));
    }

    @Test
    void usesTheRequestedBenchmarkAndNormalisesItsSymbol() throws Exception {
        String token = tokenFor("anna");
        kurse("AAA", reihe("100", HANDELSTAGE, "1.02", "0.98"));
        kurse("QQQ", reihe("100", HANDELSTAGE, "1.01", "0.99"));
        long portfolioId = portfolioMitBestand(token, "AAA", "10");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("benchmark", "qqq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkSymbol").value("QQQ"))
                .andExpect(jsonPath("$.beta").value(2.00));
    }

    @Test
    void emptyPortfolioReturnsNoMetricsInsteadOfZeros() throws Exception {
        String token = tokenFor("bruno");
        long portfolioId = createPortfolio(token);

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations").value(0))
                .andExpect(jsonPath("$.volatility").value(nullValue()))
                .andExpect(jsonPath("$.sharpeRatio").value(nullValue()))
                .andExpect(jsonPath("$.maxDrawdown").value(nullValue()))
                .andExpect(jsonPath("$.securities.length()").value(0));
    }

    @Test
    void lookbackOutsideTheAllowedRangeReturns400() throws Exception {
        String token = tokenFor("carla");
        long portfolioId = createPortfolio(token);

        for (String lookback : new String[] {"29", "3651"}) {
            mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("lookbackDays", lookback))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void blankBenchmarkReturns400() throws Exception {
        String token = tokenFor("dieter");
        long portfolioId = createPortfolio(token);

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("benchmark", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void riskOfAnotherUsersPortfolioReturns403() throws Exception {
        long portfolioId = createPortfolio(tokenFor("emil"));

        mockMvc.perform(get("/portfolios/" + portfolioId + "/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("frieda")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPortfolioReturns404() throws Exception {
        mockMvc.perform(get("/portfolios/999999/risk")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("gustav")))
                .andExpect(status().isNotFound());
    }
}
