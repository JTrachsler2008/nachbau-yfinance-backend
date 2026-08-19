package ch.allianz.youngoitv.jt.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Zentrale Abstraktion fuer externe Kursdatenquellen (ersetzt das direkte try/catch pro Methode im
 * Original, ARC-3). Alle Methoden liefern {@code Optional.empty()} statt {@code null} oder einer
 * Exception, wenn keine Daten verfuegbar sind (behebt KONV-3).
 */
public interface MarketDataProvider {

    Optional<Quote> getQuote(String symbol);

    Optional<List<HistoricalPrice>> getHistorical(String symbol, LocalDate start, LocalDate end, Interval interval);

    Optional<SecurityInfo> getInfo(String symbol);

    Optional<Snapshot> getSnapshot(String symbol);

    Optional<List<NewsItem>> getNews(String symbol, int count);

    Optional<EarningsData> getEarnings(String symbol);
}
