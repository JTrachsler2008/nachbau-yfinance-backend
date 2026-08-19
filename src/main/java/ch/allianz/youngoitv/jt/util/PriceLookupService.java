package ch.allianz.youngoitv.jt.util;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Zentrale "naechstgelegener historischer Kurs"-Utility, ersetzt die leicht abweichenden
 * Implementierungen aus mehreren Controllern des Originals. Liefert den Kurs des exakten Tages,
 * falls vorhanden, sonst den juengsten verfuegbaren Kurs davor.
 */
@Service
public class PriceLookupService {

    private final MarketDataProvider marketDataProvider;

    public PriceLookupService(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public Optional<BigDecimal> findPriceAtOrBefore(String symbol, LocalDate date) {
        return marketDataProvider.getHistorical(symbol, date.minusYears(1), date, Interval.DAILY)
                .flatMap(prices -> prices.stream()
                        .filter(price -> !price.date().isAfter(date))
                        .max(Comparator.comparing(HistoricalPrice::date))
                        .map(HistoricalPrice::close));
    }
}
