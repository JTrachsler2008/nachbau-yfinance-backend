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
class PositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Ohne diesen Mock würde jeder Test hier auf den echten, in der Testumgebung nicht erreichbaren
     * Marktdatenanbieter warten (Connect-Timeout je Symbol) - Live-Bewertung ist eigener Gegenstand
     * von {@code PositionServiceImplTest}, hier zählt nur, dass die Bestandsdaten selbst stimmen.
     */
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

    private long createAccount(String token, long portfolioId, String name) throws Exception {
        return jsonId(mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"currency\":\"CHF\"}"))
                .andReturn());
    }

    private long createSecurity(String token, String symbol) throws Exception {
        return jsonId(mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"%s","name":"%s Inc.","assetType":"STOCK","tradingCurrency":"CHF",
                                 "sector":"Technology","countryCode":"CH"}
                                """.formatted(symbol, symbol)))
                .andReturn());
    }

    private void buy(String token, long accountId, long securityId, String quantity, String price) throws Exception {
        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100000.00}"));
        mockMvc.perform(post("/accounts/" + accountId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"securityId":%d,"transactionType":"BUY","quantity":%s,"price":%s,
                                 "transactionCurrency":"CHF","transactionDate":"2026-01-05"}
                                """.formatted(securityId, quantity, price)))
                .andExpect(status().isCreated());
    }

    @Test
    void listCoversAllAccountsSortedBySymbolWithSecurityDetails() throws Exception {
        when(marketDataProvider.getQuote(any())).thenReturn(Optional.empty());
        String token = tokenFor("paula");
        long portfolioId = createPortfolio(token);
        long ersterAccount = createAccount(token, portfolioId, "Cash A");
        long zweiterAccount = createAccount(token, portfolioId, "Cash B");
        long zeta = createSecurity(token, "ZETA");
        long alpha = createSecurity(token, "ALFA");

        buy(token, ersterAccount, zeta, "10", "100");
        buy(token, zweiterAccount, alpha, "4", "25");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("ALFA"))
                .andExpect(jsonPath("$[0].accountName").value("Cash B"))
                .andExpect(jsonPath("$[0].accountId").value((int) zweiterAccount))
                .andExpect(jsonPath("$[0].securityId").value((int) alpha))
                .andExpect(jsonPath("$[0].totalQuantity").value(4.0))
                .andExpect(jsonPath("$[0].averagePurchasePrice").value(25.0))
                .andExpect(jsonPath("$[0].sector").value("Technology"))
                .andExpect(jsonPath("$[0].countryCode").value("CH"))
                .andExpect(jsonPath("$[0].tradingCurrency").value("CHF"))
                .andExpect(jsonPath("$[1].symbol").value("ZETA"))
                .andExpect(jsonPath("$[1].accountName").value("Cash A"));
    }

    @Test
    void addsLiveMarketValueAndGainWhenAQuoteIsAvailable() throws Exception {
        when(marketDataProvider.getQuote("ZETA"))
                .thenReturn(Optional.of(new Quote("ZETA", new BigDecimal("120"), "CHF", null)));
        String token = tokenFor("petra");
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId, "Cash A");
        long zeta = createSecurity(token, "ZETA");
        buy(token, accountId, zeta, "10", "100");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPrice").value(120.0))
                // 10 * 120 - 10 * 100
                .andExpect(jsonPath("$[0].marketValue").value(1200.0))
                .andExpect(jsonPath("$[0].unrealizedGainLoss").value(200.0));
    }

    @Test
    void leavesValuationFieldsEmptyWithoutALiveQuoteInsteadOfAssumingZero() throws Exception {
        when(marketDataProvider.getQuote(any())).thenReturn(Optional.empty());
        String token = tokenFor("otto");
        long portfolioId = createPortfolio(token);
        long accountId = createAccount(token, portfolioId, "Cash A");
        long zeta = createSecurity(token, "ZETA");
        buy(token, accountId, zeta, "10", "100");

        mockMvc.perform(get("/portfolios/" + portfolioId + "/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPrice").doesNotExist())
                .andExpect(jsonPath("$[0].marketValue").doesNotExist())
                .andExpect(jsonPath("$[0].unrealizedGainLoss").doesNotExist())
                // Der Bestand selbst bleibt trotzdem vollständig sichtbar.
                .andExpect(jsonPath("$[0].totalQuantity").value(10.0));
    }

    @Test
    void portfolioWithoutPositionsReturnsEmptyList() throws Exception {
        String token = tokenFor("quentin");
        long portfolioId = createPortfolio(token);

        mockMvc.perform(get("/portfolios/" + portfolioId + "/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void positionsOfAnotherUserReturns403() throws Exception {
        String eigner = tokenFor("rosa");
        long portfolioId = createPortfolio(eigner);

        String fremder = tokenFor("sven");
        mockMvc.perform(get("/portfolios/" + portfolioId + "/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fremder))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPortfolioReturns404() throws Exception {
        mockMvc.perform(get("/portfolios/999999/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("theo")))
                .andExpect(status().isNotFound());
    }
}
