package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.RebalancingEventDto;
import ch.allianz.youngoitv.jt.dto.SparplanChartPointDto;
import ch.allianz.youngoitv.jt.dto.SparplanRequestDto;
import ch.allianz.youngoitv.jt.dto.SparplanResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.RebalancingMode;
import ch.allianz.youngoitv.jt.service.SparplanSimulationService;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Rein lesende Simulation eines Sparplans (periodische Einzahlungen in mehrere Positionen) mit
 * optionalem Rebalancing - periodisch (alle X Monate auf Zielgewichtung) oder toleranzbandbasiert
 * (nur bei Abweichung &gt; X Prozentpunkte). Keine Persistenz, unabhängig von echten
 * Portfolios/Positionen (YOUNGOITV-436). Nutzt {@link PriceLookupService} für die "nächstgelegener
 * historischer Kurs"-Semantik, wie im fachlichen Plan für Sparplan-Simulationen vorgesehen.
 */
@Service
public class SparplanSimulationServiceImpl implements SparplanSimulationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SHARE_SCALE = 8;
    private static final int RESULT_SCALE = 2;

    private final PriceLookupService priceLookupService;

    public SparplanSimulationServiceImpl(PriceLookupService priceLookupService) {
        this.priceLookupService = priceLookupService;
    }

    @Override
    public SparplanResponseDto simulate(SparplanRequestDto request) {
        if (request.intervalMonths() < 1) {
            throw new InvalidSimulationParameterException("intervalMonths must be at least 1");
        }
        if (request.rebalancing() && request.rebalancingIntervalMonths() < 1) {
            throw new InvalidSimulationParameterException("rebalancingIntervalMonths must be at least 1");
        }
        Map<String, BigDecimal> weightFractions = normalizeToFractionsSummingToOne(request.weightsPercent());

        LocalDate from = request.startDate().withDayOfMonth(1);
        LocalDate to = LocalDate.now().minusDays(1);

        Map<String, BigDecimal> shares = new HashMap<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdownPercent = BigDecimal.ZERO;
        int rebalancingCount = 0;

        List<SparplanChartPointDto> chartData = new ArrayList<>();
        List<RebalancingEventDto> rebalancingEvents = new ArrayList<>();

        LocalDate cursor = from;
        int monthIndex = 0;
        while (!cursor.isAfter(to)) {
            if (monthIndex % request.intervalMonths() == 0) {
                totalInvested = totalInvested.add(request.amount());
                for (var entry : weightFractions.entrySet()) {
                    BigDecimal invest = request.amount().multiply(entry.getValue());
                    priceAt(entry.getKey(), cursor).ifPresent(price -> {
                        if (price.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal boughtShares = invest.divide(price, SHARE_SCALE, RoundingMode.HALF_UP);
                            shares.merge(entry.getKey(), boughtShares, BigDecimal::add);
                        }
                    });
                }
            }

            BigDecimal currentValue = portfolioValueAt(shares, cursor);

            if (request.rebalancing() && monthIndex > 0 && currentValue.compareTo(BigDecimal.ZERO) > 0) {
                Optional<RebalancingEventDto> event = maybeRebalance(
                        request, weightFractions, shares, cursor, monthIndex, currentValue);
                if (event.isPresent()) {
                    rebalancingEvents.add(event.get());
                    rebalancingCount++;
                }
            }

            if (currentValue.compareTo(peak) > 0) {
                peak = currentValue;
            }
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = peak.subtract(currentValue)
                        .divide(peak, SHARE_SCALE, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
                if (drawdown.compareTo(maxDrawdownPercent) > 0) {
                    maxDrawdownPercent = drawdown;
                }
            }

            chartData.add(new SparplanChartPointDto(
                    cursor, currentValue.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                    totalInvested.setScale(RESULT_SCALE, RoundingMode.HALF_UP)));

            cursor = cursor.plusMonths(1);
            monthIndex++;
        }

        BigDecimal endValue = chartData.isEmpty() ? BigDecimal.ZERO : chartData.get(chartData.size() - 1).portfolioValue();
        BigDecimal gain = endValue.subtract(totalInvested);
        BigDecimal totalReturnPercent = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? gain.divide(totalInvested, SHARE_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED)
                : BigDecimal.ZERO;
        BigDecimal cagrPercent = calculateCagrPercent(from, to, totalInvested, endValue);

        Map<String, BigDecimal> currentAllocationPercent = allocationPercent(shares, to);
        Map<String, BigDecimal> targetAllocationPercent = new LinkedHashMap<>();
        weightFractions.forEach((symbol, fraction) ->
                targetAllocationPercent.put(symbol, fraction.multiply(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP)));

        return new SparplanResponseDto(
                chartData,
                endValue,
                totalInvested.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                gain.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                totalReturnPercent.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                cagrPercent.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                maxDrawdownPercent.setScale(RESULT_SCALE, RoundingMode.HALF_UP),
                request.rebalancing(),
                request.rebalancingMode(),
                request.rebalancingBandPercent(),
                rebalancingCount,
                rebalancingEvents,
                targetAllocationPercent,
                currentAllocationPercent);
    }

    private Optional<RebalancingEventDto> maybeRebalance(
            SparplanRequestDto request,
            Map<String, BigDecimal> weightFractions,
            Map<String, BigDecimal> shares,
            LocalDate cursor,
            int monthIndex,
            BigDecimal currentValue) {
        boolean thresholdMode = request.rebalancingMode() == RebalancingMode.THRESHOLD;
        boolean dueByInterval = !thresholdMode && monthIndex % request.rebalancingIntervalMonths() == 0;
        boolean dueByThreshold = thresholdMode && isAnyAllocationOffTarget(
                weightFractions, shares, cursor, currentValue, request.rebalancingBandPercent());

        if (!dueByInterval && !dueByThreshold) {
            return Optional.empty();
        }

        Map<String, BigDecimal> pricesAtCursor = new HashMap<>();
        for (String symbol : weightFractions.keySet()) {
            Optional<BigDecimal> price = priceAt(symbol, cursor);
            if (price.isEmpty()) {
                return Optional.empty();
            }
            pricesAtCursor.put(symbol, price.get());
        }

        Map<String, BigDecimal> trades = new LinkedHashMap<>();
        for (var entry : weightFractions.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal price = pricesAtCursor.get(symbol);
            BigDecimal priorValue = shares.getOrDefault(symbol, BigDecimal.ZERO).multiply(price);
            BigDecimal targetValue = currentValue.multiply(entry.getValue());
            BigDecimal delta = targetValue.subtract(priorValue);
            trades.put(symbol, delta);
            shares.put(symbol, targetValue.divide(price, SHARE_SCALE, RoundingMode.HALF_UP));
        }

        String reason = thresholdMode ? "schwelle" : "intervall";
        return Optional.of(new RebalancingEventDto(cursor, reason, currentValue.setScale(RESULT_SCALE, RoundingMode.HALF_UP), trades));
    }

    private boolean isAnyAllocationOffTarget(
            Map<String, BigDecimal> weightFractions,
            Map<String, BigDecimal> shares,
            LocalDate cursor,
            BigDecimal currentValue,
            BigDecimal bandPercent) {
        for (var entry : weightFractions.entrySet()) {
            BigDecimal price = priceAt(entry.getKey(), cursor).orElse(null);
            if (price == null) {
                continue;
            }
            BigDecimal actualValue = shares.getOrDefault(entry.getKey(), BigDecimal.ZERO).multiply(price);
            BigDecimal actualPercent = actualValue.divide(currentValue, SHARE_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
            BigDecimal targetPercent = entry.getValue().multiply(HUNDRED);
            if (actualPercent.subtract(targetPercent).abs().compareTo(bandPercent) > 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal portfolioValueAt(Map<String, BigDecimal> shares, LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : shares.entrySet()) {
            Optional<BigDecimal> price = priceAt(entry.getKey(), date);
            if (price.isPresent()) {
                total = total.add(entry.getValue().multiply(price.get()));
            }
        }
        return total;
    }

    private Map<String, BigDecimal> allocationPercent(Map<String, BigDecimal> shares, LocalDate date) {
        BigDecimal total = portfolioValueAt(shares, date);
        Map<String, BigDecimal> allocation = new LinkedHashMap<>();
        for (var entry : shares.entrySet()) {
            BigDecimal value = priceAt(entry.getKey(), date).map(entry.getValue()::multiply).orElse(BigDecimal.ZERO);
            BigDecimal percent = total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : value.divide(total, SHARE_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);
            allocation.put(entry.getKey(), percent.setScale(RESULT_SCALE, RoundingMode.HALF_UP));
        }
        return allocation;
    }

    private BigDecimal calculateCagrPercent(LocalDate from, LocalDate to, BigDecimal invested, BigDecimal endValue) {
        long years = ChronoUnit.YEARS.between(from, to);
        if (years <= 0 || invested.compareTo(BigDecimal.ZERO) <= 0 || endValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        double cagr = (Math.pow(endValue.doubleValue() / invested.doubleValue(), 1.0 / years) - 1) * 100;
        return BigDecimal.valueOf(cagr);
    }

    private Optional<BigDecimal> priceAt(String symbol, LocalDate date) {
        return priceLookupService.findPriceAtOrBefore(symbol, date);
    }

    private Map<String, BigDecimal> normalizeToFractionsSummingToOne(Map<String, BigDecimal> weightsPercent) {
        BigDecimal totalWeight = weightsPercent.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> fractions = new LinkedHashMap<>();
        weightsPercent.forEach((symbol, weight) ->
                fractions.put(symbol, weight.divide(totalWeight, SHARE_SCALE, RoundingMode.HALF_UP)));
        return fractions;
    }
}
