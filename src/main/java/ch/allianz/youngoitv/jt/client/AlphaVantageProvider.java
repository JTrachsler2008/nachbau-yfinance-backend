package ch.allianz.youngoitv.jt.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sekundäre, kostenfreie Kursdatenquelle. Deckt nur Quote/Historical ab (kein Aequivalent im
 * Original für Info/Snapshot/News/Earnings) - für diese Methoden liefert diese Implementierung
 * konsistent {@code Optional.empty()} statt eines Fehlschlags (behebt die inkonsistente
 * Verfügbarkeit aus ARC-3).
 */
@Component
public class AlphaVantageProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageProvider.class);

    private final RestClient restClient;
    private final String apiKey;

    public AlphaVantageProvider(
            RestClient alphaVantageRestClient,
            @Value("${app.market-data.alphavantage.api-key}") String apiKey) {
        this.restClient = alphaVantageRestClient;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        try {
            Quote quote = restClient.get()
                    .uri("/query?function=GLOBAL_QUOTE&symbol={symbol}&apikey={apiKey}", symbol, apiKey)
                    .retrieve()
                    .body(Quote.class);
            return Optional.ofNullable(quote);
        } catch (RestClientException ex) {
            // ex.getMessage() kann die volle Request-URI inkl. apikey enthalten (z.B. bei
            // ResourceAccessException) - deshalb nur der Exception-Typ, nie die Message selbst.
            log.warn("AlphaVantage getQuote failed for {}: {}", symbol, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<HistoricalPrice>> getHistorical(String symbol, LocalDate start, LocalDate end, Interval interval) {
        try {
            List<HistoricalPrice> prices = restClient.get()
                    .uri("/query?function=TIME_SERIES_DAILY&symbol={symbol}&apikey={apiKey}", symbol, apiKey)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<HistoricalPrice>>() {
                    });
            return Optional.ofNullable(prices);
        } catch (RestClientException ex) {
            log.warn("AlphaVantage getHistorical failed for {}: {}", symbol, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public Optional<SecurityInfo> getInfo(String symbol) {
        return Optional.empty();
    }

    @Override
    public Optional<Snapshot> getSnapshot(String symbol) {
        return Optional.empty();
    }

    @Override
    public Optional<List<NewsItem>> getNews(String symbol, int count) {
        return Optional.empty();
    }

    @Override
    public Optional<EarningsData> getEarnings(String symbol) {
        return Optional.empty();
    }
}
