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
}
