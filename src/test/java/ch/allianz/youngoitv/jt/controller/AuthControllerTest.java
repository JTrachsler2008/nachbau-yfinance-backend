package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        userService.register("carol", "carol@example.com", "correct-password");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        userService.register("dave", "dave@example.com", "correct-password");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dave","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"whatever"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSetsARefreshCookieThatJavaScriptCannotRead() throws Exception {
        userService.register("erin", "erin@example.com", "correct-password");

        MvcResult result = login("erin", "correct-password");

        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        // Ohne HttpOnly wäre der Token per document.cookie lesbar und läge damit so offen wie im
        // localStorage - genau das soll er nicht (SEC-2).
        assertThat(cookie.isHttpOnly()).isTrue();

        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("SameSite=Strict").contains("Path=/");
    }

    @Test
    void refreshWithTheCookieReturnsANewTokenAndRotatesTheCookie() throws Exception {
        userService.register("frank", "frank@example.com", "correct-password");
        Cookie first = refreshCookieOf(login("frank", "correct-password"));

        MvcResult result = mockMvc.perform(post("/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(tokenOf(result)).isNotBlank();
        assertThat(refreshCookieOf(result).getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void refreshWithoutACookieReturns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithAnUnknownCookieReturns401() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "not-a-token")))
                .andExpect(status().isUnauthorized());
    }

    /** Der Kern der Rotation: ein abgefangener Token ist nach der ersten Einlösung wertlos. */
    @Test
    void refreshTwiceWithTheSameCookieReturns401() throws Exception {
        userService.register("grace", "grace@example.com", "correct-password");
        Cookie first = refreshCookieOf(login("grace", "correct-password"));

        mockMvc.perform(post("/auth/refresh").cookie(first)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookieAndMakesTheRefreshTokenUnusable() throws Exception {
        userService.register("heidi", "heidi@example.com", "correct-password");
        Cookie cookie = refreshCookieOf(login("heidi", "correct-password"));

        MvcResult result = mockMvc.perform(post("/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getCookie("refresh_token").getMaxAge()).isZero();

        mockMvc.perform(post("/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Abmelden ohne Cookie ist kein Fehler: der Client soll seinen Zustand aufräumen können, auch wenn
     * der Server die Sitzung längst nicht mehr kennt.
     */
    @Test
    void logoutWithoutACookieReturns204() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private MvcResult login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie refreshCookieOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Cookie("refresh_token", cookie.getValue());
    }

    private String tokenOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
