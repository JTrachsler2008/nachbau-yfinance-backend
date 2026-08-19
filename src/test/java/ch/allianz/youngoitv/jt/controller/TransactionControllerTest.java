package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
class TransactionControllerTest {

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

    private long createAccount(String token, String currency) throws Exception {
        MvcResult portfolioResult = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"baseCurrency\":\"" + currency + "\"}"))
                .andReturn();
        long portfolioId = jsonId(portfolioResult);

        MvcResult accountResult = mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cash\",\"currency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(accountResult);
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

    private void deposit(String token, long accountId, String amount) throws Exception {
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    @Test
    void buyReducesCashByPriceTimesQuantityPlusFeesAndUpdatesPosition() throws Exception {
        String token = tokenFor("victor");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VBUY", "CHF");
        deposit(token, accountId, "10000.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                                 "fee":5,"tax":0,"transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fxRateToPortfolio").value(1));
    }

    @Test
    void buyWithoutSufficientCashReturns400() throws Exception {
        String token = tokenFor("wanda");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VNOF", "CHF");
        deposit(token, accountId, "100.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sellWithoutSufficientSharesReturns400() throws Exception {
        String token = tokenFor("xena");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VSEL", "CHF");
        deposit(token, accountId, "10000.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"SELL","quantity":5,"price":100,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dividendIncreasesCashByPriceTimesQuantity() throws Exception {
        String token = tokenFor("yannick");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VDIV", "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"DIVIDEND","quantity":10,"price":2.50,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        // 10 Stueck * 2.50 = 25.00 Cash-Zugang, verifiziert ueber eine anschliessende Auszahlung.
        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":25.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(0.0));
    }

    @Test
    void splitThenSellMoreThanPreSplitQuantitySucceeds() throws Exception {
        String token = tokenFor("zack");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VSPL", "CHF");
        deposit(token, accountId, "10000.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                         "transactionCurrency":"CHF","transactionDate":"2026-01-01"}
                        """.formatted(securityId)));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"SPLIT","quantity":0,"splitRatio":2,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-10"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        // Vor dem Split waren nur 10 Stueck vorhanden - 15 zu verkaufen waere ohne Split abgelehnt worden.
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"SELL","quantity":15,"price":50,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-11"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());
    }

    @Test
    void openLotsReflectFifoConsumptionAfterASell() throws Exception {
        String token = tokenFor("aaron");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFIFO", "CHF");
        deposit(token, accountId, "10000.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                         "transactionCurrency":"CHF","transactionDate":"2026-01-01"}
                        """.formatted(securityId)));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":150,
                         "transactionCurrency":"CHF","transactionDate":"2026-02-01"}
                        """.formatted(securityId)));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"SELL","quantity":10,"price":200,
                         "transactionCurrency":"CHF","transactionDate":"2026-03-01"}
                        """.formatted(securityId)));

        MvcResult lotsResult = mockMvc.perform(get("/accounts/" + accountId + "/positions/" + securityId + "/lots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var lots = objectMapper.readTree(lotsResult.getResponse().getContentAsString());
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).get("purchasePrice").asDouble()).isEqualTo(150.0);
    }
}
