package ch.allianz.youngoitv.jt.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
}
