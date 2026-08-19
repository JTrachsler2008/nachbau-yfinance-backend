package ch.allianz.youngoitv.jt.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
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
}
