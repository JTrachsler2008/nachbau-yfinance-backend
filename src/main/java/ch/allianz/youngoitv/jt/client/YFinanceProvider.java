package ch.allianz.youngoitv.jt.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Primäre Kursdatenquelle: ruft den Python-Microservice (yfinance-Wrapper) über einen zentral
 * konfigurierten RestClient an. Jede Methode fängt RestClientException ab und liefert
 * {@code Optional.empty()} statt die Exception nach aussen durchzulassen (KONV-3).
 */
@Component
public class YFinanceProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YFinanceProvider.class);

    private final RestClient restClient;

    public YFinanceProvider(RestClient yFinanceRestClient) {
        this.restClient = yFinanceRestClient;
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        return get("/quote/{symbol}", Quote.class, symbol);
    }

    @Override
    public Optional<List<HistoricalPrice>> getHistorical(String symbol, LocalDate start, LocalDate end, Interval interval) {
        try {
            List<HistoricalPrice> prices = restClient.get()
                    .uri("/historical/{symbol}?start={start}&end={end}&interval={interval}",
                            symbol, start, end, interval)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<HistoricalPrice>>() {
                    });
            return Optional.ofNullable(prices);
        } catch (RestClientException ex) {
            log.warn("YFinance getHistorical failed for {}: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<SecurityInfo> getInfo(String symbol) {
        return get("/info/{symbol}", SecurityInfo.class, symbol);
    }

    @Override
    public Optional<Snapshot> getSnapshot(String symbol) {
        return get("/snapshot/{symbol}", Snapshot.class, symbol);
    }

    @Override
    public Optional<List<NewsItem>> getNews(String symbol, int count) {
        try {
            List<NewsItem> news = restClient.get()
                    .uri("/news/{symbol}?count={count}", symbol, count)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<NewsItem>>() {
                    });
            return Optional.ofNullable(news);
        } catch (RestClientException ex) {
            log.warn("YFinance getNews failed for {}: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<EarningsData> getEarnings(String symbol) {
        return get("/earnings/{symbol}", EarningsData.class, symbol);
    }

    private <T> Optional<T> get(String uriTemplate, Class<T> responseType, Object... uriVariables) {
        try {
            return Optional.ofNullable(restClient.get().uri(uriTemplate, uriVariables).retrieve().body(responseType));
        } catch (RestClientException ex) {
            log.warn("YFinance call to {} failed: {}", uriTemplate, ex.getMessage());
            return Optional.empty();
        }
    }
}
