package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.AssetClassDefinitionDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;
import ch.allianz.youngoitv.jt.dto.NormalizedSeriesPointDto;
import ch.allianz.youngoitv.jt.dto.PortfolioComparisonPointDto;
import ch.allianz.youngoitv.jt.dto.WeightedSymbolDto;
import ch.allianz.youngoitv.jt.service.CompareService;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Rein lesender Vergleich normalisierter (indexierter, Basis=100) Wertverläufe - Standard-Assetklassen
 * (YOUNGOITV-435) oder zwei frei definierte, hypothetische Portfolios. Verwendet ausschliesslich das
 * tatsächlich angefragte Ticker-Symbol als Referenz (behebt die im Original fehlerhafte
 * Label-zu-Symbol-Zuordnung, z.B. "MSCI World" -&gt; korrekt {@code URTH} statt {@code MSCI}, "SMI"
 * -&gt; korrekt {@code EWL} statt {@code SMI}) - es gibt keine serverseitige Namens-Uebersetzung, nur
 * die feste, dokumentierte Referenzliste unten. Nutzt für jeden Datenpunkt ausschliesslich
 * {@link PriceLookupService} (zentrale "nächstgelegener Kurs auf-oder-vor Datum"-Semantik) statt
 * einer eigenen Kurs-Lookup-Logik.
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

    private final PriceLookupService priceLookupService;

    public CompareServiceImpl(PriceLookupService priceLookupService) {
        this.priceLookupService = priceLookupService;
    }

    @Override
    public AssetClassComparisonResponseDto getAssetClassComparison(int periodYears) {
        LocalDate from = LocalDate.now().minusYears(periodYears);
        LocalDate to = LocalDate.now().minusDays(1);
        List<LocalDate> months = monthlyGrid(from, to);

        Map<String, BigDecimal> baseValues = new HashMap<>();
        Map<LocalDate, Map<String, BigDecimal>> valuesByMonth = new LinkedHashMap<>();
        Set<String> symbolsWithData = new LinkedHashSet<>();

        for (LocalDate month : months) {
            for (AssetClassDefinitionDto definition : ASSET_CLASSES) {
                BigDecimal normalized = normalizedValueAt(definition.symbol(), baseValues, month);
                if (normalized != null) {
                    symbolsWithData.add(definition.symbol());
                    valuesByMonth.computeIfAbsent(month, ignored -> new LinkedHashMap<>())
                            .put(definition.symbol(), normalized);
                }
            }
        }

        List<AssetClassDefinitionDto> available = ASSET_CLASSES.stream()
                .filter(definition -> symbolsWithData.contains(definition.symbol()))
                .toList();
        List<NormalizedSeriesPointDto> series = new ArrayList<>();
        for (LocalDate month : months) {
            Map<String, BigDecimal> values = valuesByMonth.get(month);
            if (values != null && !values.isEmpty()) {
                series.add(new NormalizedSeriesPointDto(month, values));
            }
        }
        return new AssetClassComparisonResponseDto(available, series);
    }

    @Override
    public ComparePortfoliosResponseDto comparePortfolios(ComparePortfoliosRequestDto request) {
        int periodYears = request.periodYears() != null ? request.periodYears() : 10;
        LocalDate from = LocalDate.now().minusYears(periodYears);
        LocalDate to = LocalDate.now().minusDays(1);
        List<LocalDate> months = monthlyGrid(from, to);

        Map<String, BigDecimal> baseValues = new HashMap<>();
        List<PortfolioComparisonPointDto> points = new ArrayList<>();
        for (LocalDate month : months) {
            BigDecimal valueA = weightedNormalizedValue(request.portfolioA().positions(), baseValues, month);
            BigDecimal valueB = weightedNormalizedValue(request.portfolioB().positions(), baseValues, month);
            if (valueA != null || valueB != null) {
                points.add(new PortfolioComparisonPointDto(month, valueA, valueB));
            }
        }
        return new ComparePortfoliosResponseDto(request.portfolioA().name(), request.portfolioB().name(), points);
    }

    private List<LocalDate> monthlyGrid(LocalDate from, LocalDate to) {
        List<LocalDate> months = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    /**
     * Fehlende/nicht abrufbare Kursdaten für ein einzelnes Symbol an einem Datum führen zu einem
     * degradierten Ergebnis (dieser Datenpunkt/dieses Symbol wird ausgeschlossen), nie zu einem 500.
     */
    private BigDecimal normalizedValueAt(String symbol, Map<String, BigDecimal> baseValues, LocalDate date) {
        return priceLookupService.findPriceAtOrBefore(symbol, date)
                .map(price -> {
                    baseValues.putIfAbsent(symbol, price);
                    BigDecimal base = baseValues.get(symbol);
                    if (base.compareTo(BigDecimal.ZERO) == 0) {
                        return null;
                    }
                    return price.divide(base, NORMALIZATION_SCALE, RoundingMode.HALF_UP)
                            .multiply(HUNDRED)
                            .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
                })
                .orElse(null);
    }

    private BigDecimal weightedNormalizedValue(
            List<WeightedSymbolDto> positions, Map<String, BigDecimal> baseValues, LocalDate date) {
        BigDecimal totalWeight = positions.stream().map(WeightedSymbolDto::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (WeightedSymbolDto position : positions) {
            BigDecimal normalized = normalizedValueAt(position.symbol(), baseValues, date);
            if (normalized == null) {
                return null;
            }
            BigDecimal weightFraction = position.weight().divide(totalWeight, NORMALIZATION_SCALE, RoundingMode.HALF_UP);
            total = total.add(normalized.multiply(weightFraction));
        }
        return total.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
