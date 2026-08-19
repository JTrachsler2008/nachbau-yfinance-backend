package ch.allianz.youngoitv.jt.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
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
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    private long createPortfolioAndAccount(String token, String currency) throws Exception {
        MvcResult portfolioResult = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"baseCurrency\":\"" + currency + "\"}"))
                .andReturn();
        long portfolioId = objectMapper.readTree(portfolioResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult accountResult = mockMvc.perform(post("/portfolios/" + portfolioId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cash\",\"currency\":\"" + currency + "\"}"))
                .andReturn();
        return objectMapper.readTree(accountResult.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void depositOnFreshAccountWorksWithoutNpe() throws Exception {
        String token = tokenFor("peter");
        long accountId = createPortfolioAndAccount(token, "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAmount").value(1000.00));
    }

    @Test
    void withdrawalWithoutSufficientFundsReturns400() throws Exception {
        String token = tokenFor("quentin");
        long accountId = createPortfolioAndAccount(token, "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accessingForeignAccountReturns403() throws Exception {
        String ownerToken = tokenFor("rachel");
        String strangerToken = tokenFor("sam");
        long accountId = createPortfolioAndAccount(ownerToken, "CHF");

        mockMvc.perform(post("/accounts/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00}
                                """))
                .andExpect(status().isForbidden());
    }
}
