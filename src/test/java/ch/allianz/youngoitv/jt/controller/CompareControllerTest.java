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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompareControllerTest {

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
    void assetClassesEndpointReturnsOnlyAvailableAssetClasses() throws Exception {
        when(marketDataProvider.getHistorical(eq("SPY"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("100")))));
        when(marketDataProvider.getHistorical(
                org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.equals("SPY")), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/compare/asset-classes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("wendy"))
                        .param("period", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetClasses.length()").value(1))
                .andExpect(jsonPath("$.assetClasses[0].symbol").value("SPY"))
                .andExpect(jsonPath("$.series[0].valuesBySymbol.SPY").value(100.0));
    }

    @Test
    void comparePortfoliosEndpointReturnsSeriesForBothPortfolios() throws Exception {
        when(marketDataProvider.getHistorical(eq("AAA"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("50")))));
        when(marketDataProvider.getHistorical(eq("BBB"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("200")))));

        mockMvc.perform(post("/compare/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("xander"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"portfolioA":{"name":"A","positions":[{"symbol":"AAA","weight":1}]},
                                 "portfolioB":{"name":"B","positions":[{"symbol":"BBB","weight":1}]},
                                 "periodYears":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameA").value("A"))
                .andExpect(jsonPath("$.nameB").value("B"))
                .andExpect(jsonPath("$.series[0].portfolioAValue").value(100.0))
                .andExpect(jsonPath("$.series[0].portfolioBValue").value(100.0));
    }

    @Test
    void assetClassesAcceptsAFreeDateRangeInsteadOfAPeriodPreset() throws Exception {
        when(marketDataProvider.getHistorical(eq("SPY"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("100")))));
        when(marketDataProvider.getHistorical(
                org.mockito.ArgumentMatchers.argThat(s -> s != null && !s.equals("SPY")), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.empty());
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(60);

        mockMvc.perform(get("/compare/asset-classes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("yolanda"))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        // Ohne Wirkung, weil from/to vorrangig sind.
                        .param("period", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetClasses[0].symbol").value("SPY"));
    }

    @Test
    void assetClassesWithOnlyOneOfFromOrToReturns400() throws Exception {
        mockMvc.perform(get("/compare/asset-classes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("zack"))
                        .param("from", LocalDate.now().minusDays(60).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comparePortfoliosAcceptsAFreeDateRangeInsteadOfPeriodYears() throws Exception {
        when(marketDataProvider.getHistorical(eq("AAA"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("50")))));
        when(marketDataProvider.getHistorical(eq("BBB"), any(), any(), eq(Interval.DAILY)))
                .thenReturn(Optional.of(List.of(new HistoricalPrice(LocalDate.of(2020, 1, 1), new BigDecimal("200")))));
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(60);

        mockMvc.perform(post("/compare/portfolios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("amir"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"portfolioA":{"name":"A","positions":[{"symbol":"AAA","weight":1}]},
                                 "portfolioB":{"name":"B","positions":[{"symbol":"BBB","weight":1}]},
                                 "from":"%s","to":"%s"}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[0].portfolioAValue").value(100.0));
    }
}
