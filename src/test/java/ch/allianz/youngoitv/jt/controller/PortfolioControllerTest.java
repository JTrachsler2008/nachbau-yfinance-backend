package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.repository.PortfolioRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private PortfolioRepository portfolioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    @Test
    void createAndGetOwnPortfolioSucceeds() throws Exception {
        String token = tokenFor("laura");

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Retirement","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(created.getResponse().getContentAsString());
        long portfolioId = body.get("id").asLong();

        mockMvc.perform(get("/portfolios/" + portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Retirement"));
    }

    @Test
    void accessingForeignPortfolioReturns403() throws Exception {
        String ownerToken = tokenFor("mia");
        String strangerToken = tokenFor("nick");

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mia's Portfolio","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(created.getResponse().getContentAsString());
        long portfolioId = body.get("id").asLong();

        mockMvc.perform(get("/portfolios/" + portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    /**
     * B-3 (Review Tickets): verifiziert die "keine Exception-Details"-Garantie Ende-zu-Ende ueber
     * die reale Controller-/Service-/GlobalExceptionHandler-Kette, statt nur auf Unit-Ebene oder mit
     * einer Anfrage, die bereits am Security-Filter abgefangen wird.
     */
    @Test
    void unexpectedRepositoryFailureDoesNotLeakDetailsThroughRealControllerChain() throws Exception {
        String token = tokenFor("oskar");
        doThrow(new RuntimeException("db connection string: secret-stuff"))
                .when(portfolioRepository).findById(any());

        MvcResult result = mockMvc.perform(get("/portfolios/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("secret-stuff");
        assertThat(responseBody).doesNotContain("RuntimeException");
        assertThat(responseBody).doesNotContain("at ch.allianz");
    }
}
