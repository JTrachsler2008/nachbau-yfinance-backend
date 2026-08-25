package ch.allianz.youngoitv.jt.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-minutes}")
    private long jwtExpirationMinutes;

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithInvalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithValidTokenReachesControllerWithCorrectPrincipal() throws Exception {
        // Der Benutzer muss angelegt sein, weil /users/me seit der Rollenangabe im Datenbestand
        // nachschlägt. Ein Token allein beweist nur, wer es ausgestellt hat, nicht dass es den
        // Benutzer noch gibt.
        userService.register("erin", "erin@example.com", "password123");
        String token = jwtService.generateToken("erin");

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("erin"))
                .andExpect(jsonPath("$.role").value("PRIVATANLEGER"));
    }

    @Test
    void requestWithExpiredTokenIsRejected() throws Exception {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(2, ChronoUnit.HOURS), ZoneOffset.UTC);
        JwtService expiredTokenIssuer = new JwtService(jwtSecret, jwtExpirationMinutes, twoHoursAgo);
        String expiredToken = expiredTokenIssuer.generateToken("frank");

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
