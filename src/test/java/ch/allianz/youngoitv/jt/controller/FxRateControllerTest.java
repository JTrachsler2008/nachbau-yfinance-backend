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
class FxRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private String token() {
        userService.register("uma", "uma@example.com", "password123");
        return jwtService.generateToken("uma");
    }

    private String adminToken() {
        var user = userService.register("uma", "uma@example.com", "password123");
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return jwtService.generateToken("uma");
    }

    @Test
    void findsLatestRateOnOrBeforeRequestedDate() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/fx-rates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"USD","quoteCurrency":"CHF","rateDate":"2026-01-02","rate":0.91}
                                """))
                .andExpect(status().isCreated());

        // Freitag-Kurs, abgefragt fuer Samstag (kein Kurs am Wochenende) -> juengster verfuegbarer Kurs davor.
        mockMvc.perform(get("/fx-rates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("base", "USD")
                        .param("quote", "CHF")
                        .param("date", "2026-01-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.91));
    }

    @Test
    void missingRateReturns400NotAvailable() throws Exception {
        mockMvc.perform(get("/fx-rates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .param("base", "EUR")
                        .param("quote", "JPY")
                        .param("date", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingFxRateWithoutAdminRoleReturns403() throws Exception {
        mockMvc.perform(post("/fx-rates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"EUR","quoteCurrency":"CHF","rateDate":"2026-01-02","rate":0.95}
                                """))
                .andExpect(status().isForbidden());
    }
}
