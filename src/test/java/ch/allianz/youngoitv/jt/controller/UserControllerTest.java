package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerReturnsCreatedUserWithoutPasswordHash() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ivan","email":"ivan@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo("ivan");
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(body.has("password")).isFalse();
    }

    @Test
    void registerWithDuplicateUsernameReturns409() throws Exception {
        userService.register("julia", "julia@example.com", "password123");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"julia","email":"other@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void registerIsReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"kevin","email":"kevin@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void registerIgnoresAnyRoleFieldSentByTheClient() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"laila","email":"laila@example.com","password":"password123","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("role").asText()).isEqualTo("PRIVATANLEGER");
    }

    @Test
    void meReturnsUsernameAndCurrentRole() throws Exception {
        var user = userService.register("rahel", "rahel@example.com", "password123");
        user.setRole(UserRole.MANAGER);
        userRepository.save(user);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken("rahel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rahel"))
                // Die Rolle kommt aus dem Datenbestand, nicht aus dem Token: eine Rollenänderung wirkt
                // damit ohne neue Anmeldung.
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void nonAdminCannotChangeAnyonesRoleThroughTheRealEndpoint() throws Exception {
        var caller = userService.register("marco", "marco@example.com", "password123");
        var target = userService.register("nadia", "nadia@example.com", "password123");
        String callerToken = jwtService.generateToken(caller.getUsername());

        mockMvc.perform(patch("/users/" + target.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeAnotherUsersRoleThroughTheRealEndpoint() throws Exception {
        var admin = userService.register("otto", "otto@example.com", "password123");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        var target = userService.register("petra", "petra@example.com", "password123");
        String adminToken = jwtService.generateToken(admin.getUsername());

        mockMvc.perform(patch("/users/" + target.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MANAGER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }
}
