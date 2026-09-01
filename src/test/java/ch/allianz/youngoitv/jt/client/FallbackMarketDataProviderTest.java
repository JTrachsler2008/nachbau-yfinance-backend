package ch.allianz.youngoitv.jt.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FallbackMarketDataProviderTest {

    @Mock
    private YFinanceProvider primary;

    @Mock
    private AlphaVantageProvider secondary;

    @Mock
    private MarketDataCircuitBreaker circuitBreaker;

    private FallbackMarketDataProvider fallbackProvider;

    @BeforeEach
    void setUp() {
        fallbackProvider = new FallbackMarketDataProvider(primary, secondary, circuitBreaker);
    }

    @Test
    void usesPrimaryResultWhenAvailable() {
        Quote quote = new Quote("AAPL", BigDecimal.TEN, "USD", Instant.now());
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.getQuote("AAPL")).thenReturn(Optional.of(quote));

        Optional<Quote> result = fallbackProvider.getQuote("AAPL");

        assertThat(result).contains(quote);
        verifyNoInteractions(secondary);
    }

    @Test
    void fallsBackToSecondaryWhenPrimaryReturnsEmpty() {
        Quote quote = new Quote("AAPL", BigDecimal.TEN, "USD", Instant.now());
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.getQuote("AAPL")).thenReturn(Optional.empty());
        when(secondary.getQuote("AAPL")).thenReturn(Optional.of(quote));

        Optional<Quote> result = fallbackProvider.getQuote("AAPL");

        assertThat(result).contains(quote);
    }

    /**
     * Ein Quote ohne Preis vom primären Anbieter zählt wie ein Fehlschlag und löst den Fallback aus.
     *
     * Vorher zählte schon eine vorhandene Antwort als Treffer, auch wenn ihr der Preis fehlte - der
     * Aufrufer bekam ein {@code Quote} zurück, an dem jedes spätere {@code multiply(price())} mit
     * einer NullPointerException scheiterte, statt auf den sekundären Anbieter auszuweichen.
     */
    @Test
    void treatsQuoteWithoutPriceAsAFailureAndFallsBackToSecondary() {
        Quote unvollstaendig = new Quote("AAPL", null, "USD", Instant.now());
        Quote vollstaendig = new Quote("AAPL", BigDecimal.TEN, "USD", Instant.now());
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.getQuote("AAPL")).thenReturn(Optional.of(unvollstaendig));
        when(secondary.getQuote("AAPL")).thenReturn(Optional.of(vollstaendig));

        Optional<Quote> result = fallbackProvider.getQuote("AAPL");

        assertThat(result).contains(vollstaendig);
    }

    /** Fehlt der Preis bei beiden Anbietern, bleibt es bei einem leeren Optional statt einer Exception. */
    @Test
    void quoteWithoutPriceFromBothProvidersResultsInEmptyOptional() {
        Quote unvollstaendig = new Quote("AAPL", null, "USD", Instant.now());
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.getQuote("AAPL")).thenReturn(Optional.of(unvollstaendig));
        when(secondary.getQuote("AAPL")).thenReturn(Optional.of(unvollstaendig));

        Optional<Quote> result = fallbackProvider.getQuote("AAPL");

        assertThat(result).isEmpty();
    }

    @Test
    void skipsPrimaryEntirelyWhenCircuitIsOpen() {
        Quote quote = new Quote("AAPL", BigDecimal.TEN, "USD", Instant.now());
        when(circuitBreaker.isOpen()).thenReturn(true);
        when(secondary.getQuote("AAPL")).thenReturn(Optional.of(quote));

        Optional<Quote> result = fallbackProvider.getQuote("AAPL");

        assertThat(result).contains(quote);
        verifyNoInteractions(primary);
    }

    @Test
    void bothProvidersEmptyResultsInEmptyOptionalNotException() {
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.getInfo("UNKNOWN")).thenReturn(Optional.empty());
        when(secondary.getInfo("UNKNOWN")).thenReturn(Optional.empty());

        Optional<SecurityInfo> result = fallbackProvider.getInfo("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void searchFallsBackToSecondaryWhenPrimaryUnavailable() {
        SecuritySearchResult match = new SecuritySearchResult("AAPL", "Apple Inc.", "NASDAQ", "STOCK");
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.search("AAPL")).thenReturn(Optional.empty());
        when(secondary.search("AAPL")).thenReturn(Optional.of(List.of(match)));

        Optional<List<SecuritySearchResult>> result = fallbackProvider.search("AAPL");

        assertThat(result).contains(List.of(match));
    }

    @Test
    void searchWithNoMatchesIsNotTreatedAsAFailure() {
        when(circuitBreaker.isOpen()).thenReturn(false);
        when(primary.search("ZZZZZZ")).thenReturn(Optional.of(List.of()));

        Optional<List<SecuritySearchResult>> result = fallbackProvider.search("ZZZZZZ");

        assertThat(result).contains(List.of());
        verifyNoInteractions(secondary);
    }
}
