package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.repository.PortfolioRepository;
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

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    private long registerAndGetId(String username, UserRole role) {
        var user = userService.register(username, username + "@example.com", "password123");
        user.setRole(role);
        userRepository.save(user);
        return user.getId();
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

    @Test
    void assigningAManagerGrantsThemAccessToThePortfolio() throws Exception {
        String ownerToken = tokenFor("penny");
        long managerId = registerAndGetId("quentin", UserRole.MANAGER);
        String managerToken = jwtService.generateToken("quentin");

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mandant","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/portfolios/" + portfolioId + "/manager")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerUserId\":" + managerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerUserId").value(managerId))
                .andExpect(jsonPath("$.managerUsername").value("quentin"));

        mockMvc.perform(get("/portfolios/" + portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mandant"));
    }

    @Test
    void assigningAUserWithoutManagerRoleReturns400() throws Exception {
        String ownerToken = tokenFor("rachel");
        long nonManagerId = registerAndGetId("simon", UserRole.PRIVATANLEGER);

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mandant","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/portfolios/" + portfolioId + "/manager")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerUserId\":" + nonManagerId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assigningManagerAsNonOwnerReturns403() throws Exception {
        String ownerToken = tokenFor("tara");
        String strangerToken = tokenFor("ulf");
        long managerId = registerAndGetId("vera", UserRole.MANAGER);

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mandant","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/portfolios/" + portfolioId + "/manager")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerUserId\":" + managerId + "}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Mandatsliste des Managers (YOUNGOITV-459). Geprüft wird beides zusammen: dass das betreute
     * Portfolio dort erscheint und dass es nicht zusätzlich in der eigenen Liste des Managers steht.
     * Sonst könnte ein Manager ein Mandantenportfolio für sein eigenes halten.
     */
    @Test
    void managedListContainsMandatesAndOwnListStaysSeparate() throws Exception {
        String ownerToken = tokenFor("wanda");
        long managerId = registerAndGetId("xavier", UserRole.MANAGER);
        String managerToken = jwtService.generateToken("xavier");

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mandat Wanda","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Eigenes des Managers","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/portfolios/" + portfolioId + "/manager")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerUserId\":" + managerId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/portfolios/managed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(portfolioId))
                .andExpect(jsonPath("$[0].name").value("Mandat Wanda"))
                // Der Name des Eigentümers unterscheidet ein Mandat vom eigenen Portfolio.
                .andExpect(jsonPath("$[0].ownerUsername").value("wanda"))
                .andExpect(jsonPath("$[0].managerUsername").value("xavier"));

        mockMvc.perform(get("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Eigenes des Managers"))
                .andExpect(jsonPath("$[0].ownerUsername").value("xavier"));

        mockMvc.perform(get("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(portfolioId));
    }

    /** Ohne Managerrolle gibt es keine Mandate, und das ist kein Fehler, sondern eine leere Liste. */
    @Test
    void managedListIsEmptyForAPrivateInvestor() throws Exception {
        String token = tokenFor("yara");

        mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Eigenes","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/portfolios/managed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * Nach dem Entzug der Managerrolle bleibt die Zuordnung laut Plan stehen, der Zugriff aber nicht.
     * Die Mandatsliste muss dann leer sein, sonst nennt sie Portfolios, die sich nicht mehr öffnen
     * lassen.
     */
    @Test
    void managedListDropsMandatesAfterTheManagerRoleIsRevoked() throws Exception {
        String ownerToken = tokenFor("zoe");
        long managerId = registerAndGetId("aaron", UserRole.MANAGER);
        String managerToken = jwtService.generateToken("aaron");

        MvcResult created = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mandat Zoe","baseCurrency":"CHF"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/portfolios/" + portfolioId + "/manager")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"managerUserId\":" + managerId + "}"))
                .andExpect(status().isOk());

        var manager = userService.getByUsernameOrThrow("aaron");
        manager.setRole(UserRole.PRIVATANLEGER);
        userRepository.save(manager);

        mockMvc.perform(get("/portfolios/managed")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/portfolios/" + portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
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
