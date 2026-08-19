package ch.allianz.youngoitv.jt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceLookupServiceTest {

    @Mock
    private MarketDataProvider marketDataProvider;

    @Test
    void returnsExactDatePriceWhenAvailable() {
        PriceLookupService service = new PriceLookupService(marketDataProvider);
        List<HistoricalPrice> history = List.of(
                new HistoricalPrice(LocalDate.of(2026, 1, 2), new BigDecimal("100")),
                new HistoricalPrice(LocalDate.of(2026, 1, 5), new BigDecimal("105")));
        when(marketDataProvider.getHistorical(eq("AAPL"), any(), any(), any(Interval.class)))
                .thenReturn(Optional.of(history));

        Optional<BigDecimal> price = service.findPriceAtOrBefore("AAPL", LocalDate.of(2026, 1, 5));

        assertThat(price).contains(new BigDecimal("105"));
    }

    @Test
    void returnsClosestEarlierPriceWhenExactDateMissingLikeAWeekend() {
        PriceLookupService service = new PriceLookupService(marketDataProvider);
        // Freitag 2026-01-02, Wochenende 03./04. ohne Kurs.
        List<HistoricalPrice> history = List.of(
                new HistoricalPrice(LocalDate.of(2026, 1, 2), new BigDecimal("100")));
        when(marketDataProvider.getHistorical(eq("AAPL"), any(), any(), any(Interval.class)))
                .thenReturn(Optional.of(history));

        Optional<BigDecimal> price = service.findPriceAtOrBefore("AAPL", LocalDate.of(2026, 1, 4));

        assertThat(price).contains(new BigDecimal("100"));
    }

    @Test
    void returnsEmptyInsteadOfNullOrExceptionWhenNoHistoryAvailable() {
        PriceLookupService service = new PriceLookupService(marketDataProvider);
        when(marketDataProvider.getHistorical(eq("UNKNOWN"), any(), any(), any(Interval.class)))
                .thenReturn(Optional.empty());

        Optional<BigDecimal> price = service.findPriceAtOrBefore("UNKNOWN", LocalDate.of(2026, 1, 4));

        assertThat(price).isEmpty();
    }
}
