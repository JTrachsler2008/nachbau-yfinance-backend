package ch.allianz.youngoitv.jt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
}
