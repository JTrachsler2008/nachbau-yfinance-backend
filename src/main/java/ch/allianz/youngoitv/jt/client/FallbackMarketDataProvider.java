package ch.allianz.youngoitv.jt.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Kombiniert YFinanceProvider (primär) und AlphaVantageProvider (sekundär) über eine
 * deklarative Fallback-Kette mit Circuit Breaker: solange der Circuit geschlossen ist, wird der
 * primäre Provider versucht; bei wiederholten Fehlschlägen öffnet der Circuit und nachfolgende
 * Aufrufe wechseln sofort auf den Fallback, ohne erneut auf ein Timeout zu warten.
 */
@Primary
@Component
public class FallbackMarketDataProvider implements MarketDataProvider {

    private final YFinanceProvider primary;
    private final AlphaVantageProvider secondary;
    private final MarketDataCircuitBreaker circuitBreaker;

    public FallbackMarketDataProvider(
            YFinanceProvider primary, AlphaVantageProvider secondary, MarketDataCircuitBreaker circuitBreaker) {
        this.primary = primary;
        this.secondary = secondary;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        return withFallback(primary::getQuote, secondary::getQuote, symbol);
    }

    @Override
    public Optional<List<HistoricalPrice>> getHistorical(String symbol, LocalDate start, LocalDate end, Interval interval) {
        if (!circuitBreaker.isOpen()) {
            Optional<List<HistoricalPrice>> result = primary.getHistorical(symbol, start, end, interval);
            if (result.isPresent()) {
                circuitBreaker.recordSuccess();
                return result;
            }
            circuitBreaker.recordFailure();
        }
        return secondary.getHistorical(symbol, start, end, interval);
    }

    @Override
    public Optional<SecurityInfo> getInfo(String symbol) {
        return withFallback(primary::getInfo, secondary::getInfo, symbol);
    }

    @Override
    public Optional<Snapshot> getSnapshot(String symbol) {
        return withFallback(primary::getSnapshot, secondary::getSnapshot, symbol);
    }

    @Override
    public Optional<List<NewsItem>> getNews(String symbol, int count) {
        if (!circuitBreaker.isOpen()) {
            Optional<List<NewsItem>> result = primary.getNews(symbol, count);
            if (result.isPresent()) {
                circuitBreaker.recordSuccess();
                return result;
            }
            circuitBreaker.recordFailure();
        }
        return secondary.getNews(symbol, count);
    }

    @Override
    public Optional<EarningsData> getEarnings(String symbol) {
        return withFallback(primary::getEarnings, secondary::getEarnings, symbol);
    }

    private <T> Optional<T> withFallback(
            java.util.function.Function<String, Optional<T>> primaryCall,
            java.util.function.Function<String, Optional<T>> secondaryCall,
            String symbol) {
        if (!circuitBreaker.isOpen()) {
            Optional<T> result = primaryCall.apply(symbol);
            if (result.isPresent()) {
                circuitBreaker.recordSuccess();
                return result;
            }
            circuitBreaker.recordFailure();
        }
        return secondaryCall.apply(symbol);
    }
}
