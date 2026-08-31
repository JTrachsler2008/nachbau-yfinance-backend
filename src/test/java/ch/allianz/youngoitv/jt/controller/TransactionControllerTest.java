package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        var user = userService.register(username, username + "@example.com", "password123");
        // ADMIN, damit derselbe Test-User in createSecurity() auch Stammdaten anlegen darf
        // (YOUNGOITV-441 gated die Security-/FxRate-Schreibendpunkte auf die ADMIN-Rolle).
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.generateToken(username);
    }

    private long jsonId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPortfolio(String token, String currency) throws Exception {
        MvcResult portfolioResult = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"baseCurrency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(portfolioResult);
    }

    private long createAccountIn(String token, long portfolioId, String name, String currency) throws Exception {
        MvcResult accountResult = mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currency\":\"" + currency + "\"}"))
                .andReturn();
        return jsonId(accountResult);
    }

    private long createAccount(String token, String currency) throws Exception {
        return createAccountIn(token, createPortfolio(token, currency), "Cash", currency);
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

    /** Legt einen Kurs an, damit die Umrechnung ihn findet statt live nachzuladen. */
    private void createFxRate(String token, String base, String quote, String date, String rate) throws Exception {
        mockMvc.perform(post("/fx-rates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"" + base + "\",\"quoteCurrency\":\"" + quote
                                + "\",\"rateDate\":\"" + date + "\",\"rate\":" + rate + "}"))
                .andExpect(status().isCreated());
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

        // 10000.00 Startkapital - (10 * 100 Kaufpreis + 5 Gebühr) = 8995.00 verbleibendes Cash.
        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":8995.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(0.0));

        MvcResult lotsResult = mockMvc.perform(get("/accounts/" + accountId + "/positions/" + securityId + "/lots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        var lots = objectMapper.readTree(lotsResult.getResponse().getContentAsString());
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).get("quantity").asDouble()).isEqualTo(10.0);
    }

    /**
     * Kauf in Fremdwährung: die Deckungsprüfung und der Abzug müssen den Gegenwert in der
     * Kontowährung verwenden, nicht die Fremdwährungszahl.
     *
     * USD 1000 kosten bei Kurs 0.80 CHF 800. Auf dem Konto liegen CHF 900, der Kauf ist also gedeckt.
     * Ohne Umrechnung verglich der Code CHF 900 mit der Zahl 1000 und lehnte ihn mit 400 ab.
     */
    @Test
    void buyInAForeignCurrencyChargesTheConvertedAmountToTheAccount() throws Exception {
        String token = tokenFor("nadia");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFXB", "USD");
        createFxRate(token, "USD", "CHF", "2026-01-01", "0.80");
        deposit(token, accountId, "900.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                                 "transactionCurrency":"USD","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        // CHF 900 - (USD 1000 * 0.80) = CHF 100 verbleibendes Cash.
        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(0.0));
    }

    /** Der Ø-Kaufpreis der Position bleibt in der Handelswährung, nur Cash wird umgerechnet. */
    @Test
    void buyInAForeignCurrencyKeepsThePositionCostInTheTradingCurrency() throws Exception {
        String token = tokenFor("norbert");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFXP", "USD");
        createFxRate(token, "USD", "CHF", "2026-01-01", "0.80");
        deposit(token, accountId, "900.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                         "transactionCurrency":"USD","transactionDate":"2026-01-05"}
                        """.formatted(securityId)));

        MvcResult lotsResult = mockMvc.perform(get("/accounts/" + accountId + "/positions/" + securityId + "/lots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var lots = objectMapper.readTree(lotsResult.getResponse().getContentAsString());
        assertThat(lots).hasSize(1);
        // USD 100, nicht die CHF 80 des Gegenwerts.
        assertThat(lots.get(0).get("purchasePrice").asDouble()).isEqualTo(100.0);
    }

    /** Verkauf in Fremdwährung: der Erlös wird umgerechnet gutgeschrieben. */
    @Test
    void sellInAForeignCurrencyCreditsTheConvertedProceeds() throws Exception {
        String token = tokenFor("nino");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFXS", "USD");
        createFxRate(token, "USD", "CHF", "2026-01-01", "0.80");
        deposit(token, accountId, "900.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                         "transactionCurrency":"USD","transactionDate":"2026-01-05"}
                        """.formatted(securityId)));

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"SELL","quantity":10,"price":120,
                                 "transactionCurrency":"USD","transactionDate":"2026-02-05"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        // CHF 100 Restbestand + (USD 1200 * 0.80) = CHF 1060.
        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1060.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(0.0));
    }

    /** Dividende in Fremdwährung: ebenfalls umgerechnet, sonst wächst der Saldo um die falsche Zahl. */
    @Test
    void dividendInAForeignCurrencyCreditsTheConvertedPayout() throws Exception {
        String token = tokenFor("nora");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFXD", "USD");
        createFxRate(token, "USD", "CHF", "2026-01-01", "0.80");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"DIVIDEND","quantity":10,"price":2.50,
                                 "transactionCurrency":"USD","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isCreated());

        // USD 25 * 0.80 = CHF 20, nicht CHF 25.
        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":20.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(0.0));
    }

    /**
     * Auch mit Umrechnung bleibt ein wirklich ungedeckter Kauf abgelehnt: USD 2000 kosten CHF 1600,
     * auf dem Konto liegen CHF 900.
     */
    @Test
    void buyInAForeignCurrencyWithoutSufficientCashStillReturns400() throws Exception {
        String token = tokenFor("nils");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VFXN", "USD");
        createFxRate(token, "USD", "CHF", "2026-01-01", "0.80");
        deposit(token, accountId, "900.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":20,"price":100,
                                 "transactionCurrency":"USD","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buyWithNegativeQuantityReturns400InsteadOfCreatingFreeCash() throws Exception {
        String token = tokenFor("ulla");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VNEG", "CHF");
        deposit(token, accountId, "100.00");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":-1000,"price":100,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId)))
                .andExpect(status().isBadRequest());
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

        // 10 Stück * 2.50 = 25.00 Cash-Zugang, verifiziert über eine anschliessende Auszahlung.
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

        // Vor dem Split waren nur 10 Stück vorhanden - 15 zu verkaufen wäre ohne Split abgelehnt worden.
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
    void splitWithoutSplitRatioReturns400() throws Exception {
        String token = tokenFor("yorick");
        long accountId = createAccount(token, "CHF");
        long securityId = createSecurity(token, "VNSR", "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"SPLIT","quantity":0,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-10"}
                                """.formatted(securityId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void portfolioHistoryCoversAllAccountsNewestFirstAndCarriesNames() throws Exception {
        String token = tokenFor("bianca");
        long portfolioId = createPortfolio(token, "CHF");
        long ersterAccount = createAccountIn(token, portfolioId, "Cash A", "CHF");
        long zweiterAccount = createAccountIn(token, portfolioId, "Cash B", "CHF");
        long securityId = createSecurity(token, "VHIST", "CHF");
        deposit(token, ersterAccount, "10000.00");
        deposit(token, zweiterAccount, "10000.00");

        mockMvc.perform(post("/accounts/" + ersterAccount + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":10,"price":100,
                         "transactionCurrency":"CHF","transactionDate":"2026-01-01"}
                        """.formatted(securityId)));
        mockMvc.perform(post("/accounts/" + zweiterAccount + "/transactions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"securityId":%d,"transactionType":"BUY","quantity":5,"price":120,
                         "transactionCurrency":"CHF","transactionDate":"2026-03-01"}
                        """.formatted(securityId)));

        mockMvc.perform(get("/portfolios/" + portfolioId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                // Die beiden Einzahlungen sind keine Transaktionen, daher nur die zwei Käufe.
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionDate").value("2026-03-01"))
                .andExpect(jsonPath("$[0].accountName").value("Cash B"))
                .andExpect(jsonPath("$[0].accountId").value((int) zweiterAccount))
                .andExpect(jsonPath("$[0].symbol").value("VHIST"))
                .andExpect(jsonPath("$[0].securityName").value("VHIST Inc."))
                .andExpect(jsonPath("$[1].transactionDate").value("2026-01-01"))
                .andExpect(jsonPath("$[1].accountName").value("Cash A"));
    }

    @Test
    void portfolioHistoryOfAnotherUserReturns403() throws Exception {
        String eigner = tokenFor("carlos");
        long portfolioId = createPortfolio(eigner, "CHF");

        String fremder = tokenFor("dorothea");
        mockMvc.perform(get("/portfolios/" + portfolioId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fremder))
                .andExpect(status().isForbidden());
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
