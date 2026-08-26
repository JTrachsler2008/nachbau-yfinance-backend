package ch.allianz.youngoitv.jt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MarketDataProvider marketDataProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String tokenFor(String username) {
        userService.register(username, username + "@example.com", "password123");
        return jwtService.generateToken(username);
    }

    private long createPortfolio(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test","baseCurrency":"CHF"}
                                """))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void simulatePurchaseOnEmptyPortfolioReturnsFullWeightForNewPosition() throws Exception {
        String token = tokenFor("aaron2");
        long portfolioId = createPortfolio(token);
        // Gleiche Währung wie das Portfolio (CHF), damit kein FX-Kurs benötigt wird - die
        // FX-Umrechnung selbst ist separat in SimulationServiceImplTest abgedeckt.
        when(marketDataProvider.getQuote("TSLA")).thenReturn(Optional.of(new Quote("TSLA", new BigDecimal("200"), "CHF", null)));
        when(marketDataProvider.getInfo("TSLA")).thenReturn(Optional.empty());

        mockMvc.perform(get("/simulate/purchase")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("portfolioId", String.valueOf(portfolioId))
                        .param("symbol", "TSLA")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("TSLA"))
                .andExpect(jsonPath("$.cost").value(1000.0))
                .andExpect(jsonPath("$.currentPortfolioValue").value(0.0))
                .andExpect(jsonPath("$.simulatedPortfolioValue").value(1000.0));
    }

    @Test
    void simulatePurchaseOnForeignPortfolioReturns403() throws Exception {
        String ownerToken = tokenFor("bianca2");
        long portfolioId = createPortfolio(ownerToken);
        String strangerToken = tokenFor("carlo2");

        mockMvc.perform(get("/simulate/purchase")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .param("portfolioId", String.valueOf(portfolioId))
                        .param("symbol", "TSLA")
                        .param("quantity", "5"))
                .andExpect(status().isForbidden());
    }

    @Test
    void backtestReturnsGainSincePurchaseDate() throws Exception {
        LocalDate buyDate = LocalDate.of(2024, 1, 2);
        when(marketDataProvider.getHistorical(eq("MSFT"), eq(buyDate), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(buyDate, new BigDecimal("300")))));
        when(marketDataProvider.getQuote("MSFT")).thenReturn(Optional.of(new Quote("MSFT", new BigDecimal("330"), "USD", null)));

        mockMvc.perform(get("/simulate/backtest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("dana2"))
                        .param("symbol", "MSFT")
                        .param("quantity", "1")
                        .param("purchaseDate", buyDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAtBuy").value(300.0))
                .andExpect(jsonPath("$.currentPrice").value(330.0))
                .andExpect(jsonPath("$.gainLoss").value(30.0));
    }

    @Test
    void backtestWithoutHistoricalDataReturns400() throws Exception {
        when(marketDataProvider.getHistorical(eq("NOPE"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/simulate/backtest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("elin2"))
                        .param("symbol", "NOPE")
                        .param("quantity", "1")
                        .param("purchaseDate", "2024-01-01"))
                .andExpect(status().isBadRequest());
    }
}
