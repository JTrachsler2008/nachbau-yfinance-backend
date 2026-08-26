package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.CompareService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zeitraum wahlweise als Preset in Jahren ({@code period}/{@code periodYears}) oder frei über
 * {@code from}/{@code to} - werden beide Daten mitgeschickt, haben sie Vorrang, genau wie beim
 * Risiko-Endpunkt ({@code RiskController}).
 */
@RestController
@RequestMapping("/compare")
public class CompareController {

    private static final int MAX_PERIOD_YEARS = 100;
    private static final int MIN_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 100 * 366;

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/asset-classes")
    public AssetClassComparisonResponseDto assetClasses(
            @RequestParam(defaultValue = "10") int period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] range = resolveRange(period, from, to);
        return compareService.getAssetClassComparison(range[0], range[1]);
    }

    @PostMapping("/portfolios")
    public ComparePortfoliosResponseDto portfolios(@Valid @RequestBody ComparePortfoliosRequestDto request) {
        if (request.from() != null && request.to() != null) {
            requireValidRange(request.from(), request.to());
        }
        return compareService.comparePortfolios(request);
    }

    private LocalDate[] resolveRange(int periodYears, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            if (periodYears < 1 || periodYears > MAX_PERIOD_YEARS) {
                throw new InvalidSimulationParameterException(
                        "period must be between 1 and " + MAX_PERIOD_YEARS + " years");
            }
            LocalDate resolvedTo = LocalDate.now().minusDays(1);
            return new LocalDate[] {resolvedTo.minusYears(periodYears), resolvedTo};
        }
        if (from == null || to == null) {
            throw new InvalidSimulationParameterException("from and to must both be given for a custom range");
        }
        requireValidRange(from, to);
        return new LocalDate[] {from, to};
    }

    private void requireValidRange(LocalDate from, LocalDate to) {
        if (to.isAfter(LocalDate.now().minusDays(1))) {
            throw new InvalidSimulationParameterException("to must not be after yesterday");
        }
        if (!from.isBefore(to)) {
            throw new InvalidSimulationParameterException("from must be before to");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < MIN_RANGE_DAYS || days > MAX_RANGE_DAYS) {
            throw new InvalidSimulationParameterException(
                    "the range between from and to must be between " + MIN_RANGE_DAYS + " and "
                            + MAX_RANGE_DAYS + " days");
        }
    }
}
