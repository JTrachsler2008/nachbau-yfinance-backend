package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.HistoricalPrice;
import ch.allianz.youngoitv.jt.client.Interval;
import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.AssetClassDefinitionDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;
import ch.allianz.youngoitv.jt.dto.NormalizedSeriesPointDto;
import ch.allianz.youngoitv.jt.dto.PortfolioComparisonPointDto;
import ch.allianz.youngoitv.jt.dto.WeightedSymbolDto;
import ch.allianz.youngoitv.jt.service.CompareService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Rein lesender Vergleich normalisierter (indexierter, Basis=100) Wertverlaeufe - Standard-Assetklassen
 * (YOUNGOITV-435) oder zwei frei definierte, hypothetische Portfolios. Verwendet ausschliesslich das
 * tatsaechlich angefragte Ticker-Symbol als Referenz (behebt die im Original fehlerhafte
 * Label-zu-Symbol-Zuordnung, z.B. "MSCI World" -&gt; korrekt {@code URTH} statt {@code MSCI}, "SMI"
 * -&gt; korrekt {@code EWL} statt {@code SMI}) - es gibt keine serverseitige Namens-Uebersetzung, nur
 * die feste, dokumentierte Referenzliste unten.
 */
@Service
public class CompareServiceImpl implements CompareService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int NORMALIZATION_SCALE = 6;
    private static final int RESULT_SCALE = 2;

    private static final List<AssetClassDefinitionDto> ASSET_CLASSES = List.of(
            new AssetClassDefinitionDto("SPY", "Aktien (S&P 500)"),
            new AssetClassDefinitionDto("URTH", "Aktien (MSCI World)"),
            new AssetClassDefinitionDto("EWL", "Aktien (SMI Schweiz)"),
            new AssetClassDefinitionDto("QQQ", "Tech (Nasdaq 100)"),
            new AssetClassDefinitionDto("VNQ", "Immobilien (REITs)"),
            new AssetClassDefinitionDto("GLD", "Gold"),
            new AssetClassDefinitionDto("AGG", "Anleihen"),
            new AssetClassDefinitionDto("BTC-USD", "Bitcoin"));

    private final MarketDataProvider marketDataProvider;

    public CompareServiceImpl(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public AssetClassComparisonResponseDto getAssetClassComparison(int periodYears) {
        LocalDate from = LocalDate.now().minusYears(periodYears);
        LocalDate to = LocalDate.now().minusDays(1);

        Map<String, NavigableMap<LocalDate, BigDecimal>> seriesBySymbol = new LinkedHashMap<>();
        List<AssetClassDefinitionDto> available = new ArrayList<>();
        for (AssetClassDefinitionDto definition : ASSET_CLASSES) {
            NavigableMap<LocalDate, BigDecimal> series = fetchMonthlySeries(definition.symbol(), from, to);
            if (series != null) {
                seriesBySymbol.put(definition.symbol(), series);
                available.add(definition);
            }
        }

        List<LocalDate> allDates = collectSortedDates(seriesBySymbol.values());
        Map<String, BigDecimal> baseValues = new HashMap<>();
        List<NormalizedSeriesPointDto> points = new ArrayList<>();
        for (LocalDate date : allDates) {
            Map<String, BigDecimal> valuesBySymbol = new LinkedHashMap<>();
            for (AssetClassDefinitionDto definition : available) {
                BigDecimal normalized = normalizedValueAt(seriesBySymbol.get(definition.symbol()), baseValues, definition.symbol(), date);
                if (normalized != null) {
                    valuesBySymbol.put(definition.symbol(), normalized);
                }
            }
            if (!valuesBySymbol.isEmpty()) {
                points.add(new NormalizedSeriesPointDto(date, valuesBySymbol));
            }
        }
        return new AssetClassComparisonResponseDto(available, points);
    }

    @Override
    public ComparePortfoliosResponseDto comparePortfolios(ComparePortfoliosRequestDto request) {
        int periodYears = request.periodYears() != null ? request.periodYears() : 10;
        LocalDate from = LocalDate.now().minusYears(periodYears);
        LocalDate to = LocalDate.now().minusDays(1);

        Set<String> allSymbols = new LinkedHashSet<>();
        request.portfolioA().positions().forEach(w -> allSymbols.add(w.symbol()));
        request.portfolioB().positions().forEach(w -> allSymbols.add(w.symbol()));

        Map<String, NavigableMap<LocalDate, BigDecimal>> seriesBySymbol = new HashMap<>();
        for (String symbol : allSymbols) {
            NavigableMap<LocalDate, BigDecimal> series = fetchMonthlySeries(symbol, from, to);
            if (series != null) {
                seriesBySymbol.put(symbol, series);
            }
        }

        List<LocalDate> allDates = collectSortedDates(seriesBySymbol.values());
        Map<String, BigDecimal> baseValues = new HashMap<>();
        List<PortfolioComparisonPointDto> points = new ArrayList<>();
        for (LocalDate date : allDates) {
            BigDecimal valueA = weightedNormalizedValue(request.portfolioA().positions(), seriesBySymbol, baseValues, date);
            BigDecimal valueB = weightedNormalizedValue(request.portfolioB().positions(), seriesBySymbol, baseValues, date);
            if (valueA != null || valueB != null) {
                points.add(new PortfolioComparisonPointDto(date, valueA, valueB));
            }
        }
        return new ComparePortfoliosResponseDto(request.portfolioA().name(), request.portfolioB().name(), points);
    }

    /**
     * Fehlende/nicht abrufbare Kursdaten fuer ein einzelnes Symbol fuehren zu einem degradierten
     * Ergebnis (dieses Symbol wird aus dem Vergleich ausgeschlossen), nie zu einem 500.
     */
    private NavigableMap<LocalDate, BigDecimal> fetchMonthlySeries(String symbol, LocalDate from, LocalDate to) {
        return marketDataProvider.getHistorical(symbol, from, to, Interval.MONTHLY)
                .filter(prices -> !prices.isEmpty())
                .map(prices -> {
                    NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
                    for (HistoricalPrice price : prices) {
                        series.put(price.date().withDayOfMonth(1), price.close());
                    }
                    return series;
                })
                .orElse(null);
    }

    private List<LocalDate> collectSortedDates(Iterable<NavigableMap<LocalDate, BigDecimal>> allSeries) {
        Set<LocalDate> dates = new java.util.TreeSet<>();
        for (var series : allSeries) {
            dates.addAll(series.keySet());
        }
        return new ArrayList<>(dates);
    }

    private BigDecimal normalizedValueAt(
            NavigableMap<LocalDate, BigDecimal> series, Map<String, BigDecimal> baseValues, String symbol, LocalDate date) {
        var entry = series.floorEntry(date);
        if (entry == null) {
            return null;
        }
        baseValues.putIfAbsent(symbol, entry.getValue());
        BigDecimal base = baseValues.get(symbol);
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return entry.getValue().divide(base, NORMALIZATION_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedNormalizedValue(
            List<WeightedSymbolDto> positions,
            Map<String, NavigableMap<LocalDate, BigDecimal>> seriesBySymbol,
            Map<String, BigDecimal> baseValues,
            LocalDate date) {
        BigDecimal totalWeight = positions.stream().map(WeightedSymbolDto::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (WeightedSymbolDto position : positions) {
            var series = seriesBySymbol.get(position.symbol());
            if (series == null) {
                return null;
            }
            BigDecimal normalized = normalizedValueAt(series, baseValues, position.symbol(), date);
            if (normalized == null) {
                return null;
            }
            BigDecimal weightFraction = position.weight().divide(totalWeight, NORMALIZATION_SCALE, RoundingMode.HALF_UP);
            total = total.add(normalized.multiply(weightFraction));
        }
        return total.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
