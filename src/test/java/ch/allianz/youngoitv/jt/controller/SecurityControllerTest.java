package ch.allianz.youngoitv.jt.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.client.SecurityInfo;
import ch.allianz.youngoitv.jt.client.SecuritySearchResult;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityControllerTest {

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

    private String token() {
        userService.register("tina", "tina@example.com", "password123");
        return jwtService.generateToken("tina");
    }

    private String userToken(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    private String adminToken(String username) {
        var user = userService.register(username, username + "@example.com", "password123");
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.generateToken(username);
    }

    @Test
    void creatingBondWithCouponRateAndMaturityDateSucceeds() throws Exception {
        mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken("tina"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"BOND1","name":"Test Bond","assetType":"BOND",
                                 "tradingCurrency":"CHF","couponRate":2.5,"maturityDate":"2030-01-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponRate").value(2.5));
    }

    @Test
    void settingCouponRateOnNonBondReturns400() throws Exception {
        mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken("tina"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"STOCK1","name":"Test Stock","assetType":"STOCK",
                                 "tradingCurrency":"CHF","couponRate":2.5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupBySymbolReturnsTheCreatedSecurity() throws Exception {
        String token = adminToken("tina");
        mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"AAPL","name":"Apple Inc.","assetType":"STOCK","tradingCurrency":"USD"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/securities/AAPL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Apple Inc."));
    }

    @Test
    void unknownSymbolReturns404() throws Exception {
        mockMvc.perform(get("/securities/DOESNOTEXIST")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIsReadableWithoutAdminRoleAndSortedBySymbol() throws Exception {
        String adminToken = adminToken("tina");
        mockMvc.perform(post("/securities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"symbol":"ZZZZ","name":"Zeta AG","assetType":"STOCK","tradingCurrency":"CHF"}
                        """));
        mockMvc.perform(post("/securities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"symbol":"AAAA","name":"Alpha AG","assetType":"STOCK","tradingCurrency":"CHF"}
                        """));

        // Lesen darf jeder angemeldete Benutzer, das Auswahlfeld im Transaktionsformular braucht die
        // Liste. Anlegen bleibt der ADMIN-Rolle vorbehalten (siehe Test darunter).
        mockMvc.perform(get("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("nadia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAAA"))
                .andExpect(jsonPath("$[?(@.symbol == 'ZZZZ')].name").value("Zeta AG"));
    }

    @Test
    void creatingSecurityWithoutAdminRoleReturns403() throws Exception {
        mockMvc.perform(post("/securities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"NOPE","name":"Not Allowed","assetType":"STOCK","tradingCurrency":"CHF"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchReturnsLiveMatchesWithoutRequiringAdminRole() throws Exception {
        when(marketDataProvider.search("Apple"))
                .thenReturn(Optional.of(List.of(
                        new SecuritySearchResult("AAPL", "Apple Inc.", "NASDAQ", "STOCK"))));

        mockMvc.perform(get("/securities/search")
                        .param("query", "Apple")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."));
    }

    @Test
    void searchWithoutMatchesReturnsAnEmptyListInsteadOfAnError() throws Exception {
        when(marketDataProvider.search("ZZZZZZ")).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/securities/search")
                        .param("query", "ZZZZZZ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lookupOrCreateRegistersANewSecurityFromLiveMarketData() throws Exception {
        when(marketDataProvider.getQuote("AAPL"))
                .thenReturn(Optional.of(new Quote("AAPL", new BigDecimal("310.34"), "USD", null)));
        when(marketDataProvider.search("AAPL"))
                .thenReturn(Optional.of(List.of(
                        new SecuritySearchResult("AAPL", "Apple Inc.", "NASDAQ", "STOCK"))));
        when(marketDataProvider.getInfo("AAPL"))
                .thenReturn(Optional.of(new SecurityInfo("AAPL", "Apple Inc.", "Technology", "Consumer Electronics", "United States")));

        mockMvc.perform(post("/securities/lookup-or-create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"aapl"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.name").value("Apple Inc."))
                .andExpect(jsonPath("$.assetType").value("STOCK"))
                .andExpect(jsonPath("$.tradingCurrency").value("USD"))
                .andExpect(jsonPath("$.exchangeCode").value("NASDAQ"))
                .andExpect(jsonPath("$.sector").value("Technology"))
                .andExpect(jsonPath("$.countryCode").value("US"));
    }

    @Test
    void lookupOrCreateReturnsTheExistingSecurityInsteadOfADuplicate() throws Exception {
        String adminToken = adminToken("tina");
        mockMvc.perform(post("/securities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"symbol":"NESN.SW","name":"Nestlé SA","assetType":"STOCK","tradingCurrency":"CHF"}
                        """));

        mockMvc.perform(post("/securities/lookup-or-create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("nadia2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"NESN.SW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nestlé SA"));
        // Ohne Kurs-Stub: schlüge lookupOrCreate hier neu an, würde der Test mit einem 404 statt
        // dem erwarteten Ergebnis scheitern - der Beweis, dass tatsächlich das bestehende
        // Wertpapier zurückkam und keines neu angelegt wurde.
    }

    @Test
    void lookupOrCreateWithUnknownSymbolReturns404() throws Exception {
        when(marketDataProvider.getQuote("DOESNOTEXIST")).thenReturn(Optional.empty());

        mockMvc.perform(post("/securities/lookup-or-create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symbol":"doesnotexist"}
                                """))
                .andExpect(status().isNotFound());
    }
}
