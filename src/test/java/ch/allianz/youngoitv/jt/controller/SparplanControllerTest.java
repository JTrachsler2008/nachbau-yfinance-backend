package ch.allianz.youngoitv.jt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SparplanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    @Test
    void sparplanWithConstantPriceReturnsExpectedEndValue() throws Exception {
        when(marketDataProvider.getHistorical(eq("SPY"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("100")))));

        mockMvc.perform(get("/simulate/sparplan")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("yannis"))
                        .param("startDate", LocalDate.now().minusMonths(1).withDayOfMonth(1).toString())
                        .param("amount", "1000")
                        .param("intervalMonths", "1")
                        .param("positions", "SPY:100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endValue").value(2000.0))
                .andExpect(jsonPath("$.invested").value(2000.0));
    }

    @Test
    void invalidPositionsFormatReturns400() throws Exception {
        mockMvc.perform(get("/simulate/sparplan")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("zoe"))
                        .param("startDate", LocalDate.now().minusMonths(1).toString())
                        .param("amount", "1000")
                        .param("positions", "not-a-valid-format"))
                .andExpect(status().isBadRequest());
    }
}
