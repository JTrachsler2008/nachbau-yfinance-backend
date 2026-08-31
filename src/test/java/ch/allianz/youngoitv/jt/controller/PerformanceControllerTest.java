package ch.allianz.youngoitv.jt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Ohne diesen Mock würde jeder Test hier auf den in der Testumgebung nicht erreichbaren echten
     * Marktdatenanbieter warten (Connect-Timeout je Symbol).
     */
    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        var user = userService.register(username, username + "@example.com", "password123");
        // ADMIN, damit derselbe Test-User in createSecurity()/fx-rates auch Stammdaten anlegen darf.
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.generateToken(username);
    }

    private long jsonId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPortfolio(String token, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"baseCurrency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(result);
    }

    private long createAccount(String token, long portfolioId, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cash\",\"currency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(result);
    }

    private long createSecurity(String token, String symbol, String currency) throws Exception {
        MvcResult result = mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"" + symbol + "\",\"name\":\"" + symbol
                                + " Inc.\",\"assetType\":\"STOCK\",\"tradingCurrency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(result);
    }

    private void transact(String token, long accountId, String securityId, String type, String extraFields) throws Exception {
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"securityId\":" + securityId + ",\"transactionType\":\"" + type + "\"," + extraFields + "}"));
    }

    @Test
    void realizedGainsSumsFifoGainsInPortfolioCurrency() throws Exception {
        String token = tokenFor("bella");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PBUY", "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));

        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-01-01\"");
        transact(token, accountId, String.valueOf(securityId), "SELL",
                "\"quantity\":10,\"price\":150,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-02-01\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/realized-gains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.0));
    }

    @Test
    void dividendsSumsAllDividendPaymentsInPortfolioCurrency() throws Exception {
        String token = tokenFor("carl");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PDIV", "CHF");

        transact(token, accountId, String.valueOf(securityId), "DIVIDEND",
                "\"quantity\":10,\"price\":2.50,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-01-05\"");
        transact(token, accountId, String.valueOf(securityId), "DIVIDEND",
                "\"quantity\":10,\"price\":1.50,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-04-05\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/dividends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(40.0));
    }

    /**
     * Zinsertrag netto: 10 * 2.50 - 1.00 Gebühr - 5.00 Steuer = 19.00, dazu 10 * 3.00 = 30.00 ohne
     * Abzüge - zusammen 49.00. Netto, weil genau dieser Betrag auf dem Konto ankommt
     * ({@code applyCoupon}).
     */
    @Test
    void interestSumsAllCouponPaymentsNetOfFeeAndTax() throws Exception {
        String token = tokenFor("paula");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PCPN", "CHF");

        transact(token, accountId, String.valueOf(securityId), "COUPON",
                "\"quantity\":10,\"price\":2.50,\"fee\":1.00,\"tax\":5.00,"
                        + "\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-06-30\"");
        transact(token, accountId, String.valueOf(securityId), "COUPON",
                "\"quantity\":10,\"price\":3.00,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-12-31\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/interest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(49.0))
                .andExpect(jsonPath("$.currency").value("CHF"));
    }

    /**
     * Zins und Dividende bleiben getrennt: in einer gemeinsamen Summe liesse sich der Anleiheertrag
     * nachträglich nicht mehr herausrechnen.
     */
    @Test
    void interestAndDividendsDoNotCountEachOthersPayments() throws Exception {
        String token = tokenFor("quirin");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PMIX", "CHF");

        transact(token, accountId, String.valueOf(securityId), "DIVIDEND",
                "\"quantity\":10,\"price\":2.00,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-04-05\"");
        transact(token, accountId, String.valueOf(securityId), "COUPON",
                "\"quantity\":10,\"price\":3.00,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-06-30\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/dividends")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(20.0));
        mockMvc.perform(get("/portfolios/" + portfolioId + "/interest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.0));
    }

    /** Ohne Coupon-Buchung ist der Zinsertrag 0 und nicht leer: die Aussage ist "es gab keinen". */
    @Test
    void interestIsZeroWithoutAnyCouponPayment() throws Exception {
        String token = tokenFor("rahel");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/interest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0.0));
    }

    @Test
    void interestOfAnotherUsersPortfolioIsForbidden() throws Exception {
        long portfolioId = createPortfolio(tokenFor("sven"), "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/interest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("tanja"))
                        .param("currency", "CHF"))
                .andExpect(status().isForbidden());
    }

    @Test
    void realizedGainsAcrossDifferentTradingCurrencyAreConvertedBeforeSumming() throws Exception {
        String token = tokenFor("dana");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "USD");
        long securityId = createSecurity(token, "PUSD", "USD");

        mockMvc.perform(post("/fx-rates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"baseCurrency":"USD","quoteCurrency":"CHF","rateDate":"2026-01-01","rate":0.9}
                        """));
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));

        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"USD\",\"transactionDate\":\"2026-01-02\"");
        transact(token, accountId, String.valueOf(securityId), "SELL",
                "\"quantity\":10,\"price\":150,\"transactionCurrency\":\"USD\",\"transactionDate\":\"2026-02-01\"");

        // Gewinn in USD: 10*(150-100) = 500 USD; umgerechnet mit 0.9 = 450 CHF.
        mockMvc.perform(get("/portfolios/" + portfolioId + "/realized-gains")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("currency", "CHF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(450.0));
    }

    @Test
    void valuationSumsMarketValueCostBasisAndGainFromALiveQuote() throws Exception {
        when(marketDataProvider.getQuote("PVAL")).thenReturn(
                Optional.of(new Quote("PVAL", new BigDecimal("120"), "CHF", null)));
        String token = tokenFor("erik");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PVAL", "CHF");
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));
        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-01-01\"");

        // Marktwert 10*120 = 1200, Einstand 10*100 = 1000, Gewinn 200.
        mockMvc.perform(get("/portfolios/" + portfolioId + "/valuation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.marketValue").value(1200.0))
                .andExpect(jsonPath("$.costBasis").value(1000.0))
                .andExpect(jsonPath("$.unrealizedGainLoss").value(200.0))
                .andExpect(jsonPath("$.excludedSymbols").isEmpty());
    }

    @Test
    void valuationLeavesFieldsEmptyAndListsTheSymbolWithoutALiveQuote() throws Exception {
        when(marketDataProvider.getQuote(any())).thenReturn(Optional.empty());
        String token = tokenFor("fiona");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PNQ", "CHF");
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));
        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2026-01-01\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/valuation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketValue").doesNotExist())
                .andExpect(jsonPath("$.costBasis").doesNotExist())
                .andExpect(jsonPath("$.unrealizedGainLoss").doesNotExist())
                .andExpect(jsonPath("$.excludedSymbols[0]").value("PNQ"));
    }

    @Test
    void valuationIsZeroForAPortfolioWithoutAnyHoldings() throws Exception {
        String token = tokenFor("gustav");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/valuation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marketValue").value(0.0))
                .andExpect(jsonPath("$.costBasis").value(0.0))
                .andExpect(jsonPath("$.unrealizedGainLoss").value(0.0));
    }

    @Test
    void returnsComputesTheMoneyWeightedReturnFromRealCashFlows() throws Exception {
        // Kauf vor genau 365 Tagen fuer 1000, heutiger Marktwert 1100: -1000 + 1100/(1+r) = 0
        // => r = 0.10 (10%), exakt von Hand nachvollziehbar wie in MwrServiceTest.
        LocalDate purchaseDate = LocalDate.now().minusDays(365);
        when(marketDataProvider.getQuote("PMWR")).thenReturn(
                Optional.of(new Quote("PMWR", new BigDecimal("110"), "CHF", null)));
        String token = tokenFor("hanna");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PMWR", "CHF");
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));
        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"" + purchaseDate + "\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeWeightedReturn").doesNotExist())
                .andExpect(jsonPath("$.moneyWeightedReturn").value(10.0));
    }

    @Test
    void returnsIsNullForAPortfolioWithoutAnyTransactions() throws Exception {
        String token = tokenFor("ivo");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moneyWeightedReturn").doesNotExist());
    }

    /**
     * Der Wertverlauf und die zeitgewichtete Rendite kommen aus einem Aufruf. Der Kurs steigt im März
     * von 100 auf 120, also 20 % - und weil vor dem Zeitraum gekauft wurde, ist das auch die ganze
     * Rendite des Zeitraums.
     */
    @Test
    void historyReturnsTheValueSeriesAndTheTimeWeightedReturn() throws Exception {
        givenHistorical("PHIS", date -> date.isBefore(LocalDate.of(2026, 3, 1)) ? 100 : 120);
        givenHistorical("SPY", date -> 400);
        String token = tokenFor("jonas");
        long portfolioId = createPortfolio(token, "CHF");
        long accountId = createAccount(token, portfolioId, "CHF");
        long securityId = createSecurity(token, "PHIS", "CHF");
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000}"));
        transact(token, accountId, String.valueOf(securityId), "BUY",
                "\"quantity\":10,\"price\":100,\"transactionCurrency\":\"CHF\",\"transactionDate\":\"2025-12-01\"");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("from", "2026-01-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.seriesFrom").value("2026-01-01"))
                // Kein Grund: die Reihe beginnt am angefragten Tag, weil vor dem Zeitraum gekauft wurde.
                .andExpect(jsonPath("$.seriesFromReason").doesNotExist())
                .andExpect(jsonPath("$.benchmarkSymbol").value("SPY"))
                .andExpect(jsonPath("$.timeWeightedReturn").value(20.0))
                .andExpect(jsonPath("$.benchmarkReturn").value(0.0))
                .andExpect(jsonPath("$.points[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$.points[0].value").value(1000.0))
                .andExpect(jsonPath("$.points[0].invested").value(1000.0))
                .andExpect(jsonPath("$.points[0].index").value(100.0))
                .andExpect(jsonPath("$.points[0].benchmarkIndex").value(100.0))
                .andExpect(jsonPath("$.excluded").isEmpty());
    }

    /** Ohne Bestand keine Rendite: eine 0 % wäre eine Aussage über ein Portfolio, das es nicht gab. */
    @Test
    void historyHasNoReturnForAPortfolioWithoutAnyTransactions() throws Exception {
        givenHistorical("SPY", date -> 400);
        String token = tokenFor("klara");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("lookbackDays", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeWeightedReturn").doesNotExist())
                .andExpect(jsonPath("$.points[0].value").value(0.0))
                .andExpect(jsonPath("$.points[0].index").doesNotExist());
    }

    /** Dieselben Grenzen wie bei der Risikoanalyse, weil dieselbe Auflösung dahinter steht. */
    @Test
    void historyRejectsALookbackOutsideTheAllowedRange() throws Exception {
        String token = tokenFor("lena");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("lookbackDays", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyRejectsABlankBenchmark() throws Exception {
        String token = tokenFor("mira");
        long portfolioId = createPortfolio(token, "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("benchmark", "  "))
                .andExpect(status().isBadRequest());
    }

    /** Fremdes Portfolio: die Eigentumsprüfung steckt im Dienst, der Endpunkt darf sie nicht umgehen. */
    @Test
    void historyOfAnotherUsersPortfolioIsForbidden() throws Exception {
        long portfolioId = createPortfolio(tokenFor("nadja"), "CHF");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("oskar")))
                .andExpect(status().isForbidden());
    }

    /**
     * Tageskurse für ein Symbol, so dass der Dienst für jeden Tag des angefragten Fensters einen
     * Schlusskurs findet - inklusive des Vorlaufs, den er vor dem Zeitraum zusätzlich anfordert.
     */
    private void givenHistorical(String symbol, java.util.function.ToIntFunction<LocalDate> close) {
        when(marketDataProvider.getHistorical(
                        org.mockito.ArgumentMatchers.eq(symbol), any(), any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate start = invocation.getArgument(1);
                    LocalDate end = invocation.getArgument(2);
                    var history = new java.util.ArrayList<ch.allianz.youngoitv.jt.client.HistoricalPrice>();
                    for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                        history.add(new ch.allianz.youngoitv.jt.client.HistoricalPrice(
                                date, BigDecimal.valueOf(close.applyAsInt(date))));
                    }
                    return Optional.of(history);
                });
    }
}
