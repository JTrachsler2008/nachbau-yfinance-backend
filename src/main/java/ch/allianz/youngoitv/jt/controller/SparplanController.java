package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.SparplanRequestDto;
import ch.allianz.youngoitv.jt.dto.SparplanResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.RebalancingMode;
import ch.allianz.youngoitv.jt.service.SparplanSimulationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate")
public class SparplanController {

    private static final int MAX_YEARS_BACK = 40;
    private static final int MAX_POSITIONS = 20;
    private static final int MAX_PRECISION = 20;
    private static final int MAX_SCALE = 20;

    private final SparplanSimulationService sparplanSimulationService;

    public SparplanController(SparplanSimulationService sparplanSimulationService) {
        this.sparplanSimulationService = sparplanSimulationService;
    }

    @GetMapping("/sparplan")
    public SparplanResponseDto sparplan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "1") int intervalMonths,
            @RequestParam String positions,
            @RequestParam(defaultValue = "false") boolean rebalancing,
            @RequestParam(defaultValue = "12") int rebalancingIntervalMonths,
            @RequestParam(defaultValue = "INTERVAL") RebalancingMode rebalancingMode,
            @RequestParam(defaultValue = "10") BigDecimal rebalancingBandPercent) {

        if (startDate.isBefore(LocalDate.now().minusYears(MAX_YEARS_BACK))) {
            throw new InvalidSimulationParameterException("startDate must not be more than " + MAX_YEARS_BACK + " years in the past");
        }
        if (intervalMonths < 1) {
            throw new InvalidSimulationParameterException("intervalMonths must be at least 1");
        }
        if (rebalancingIntervalMonths < 1) {
            throw new InvalidSimulationParameterException("rebalancingIntervalMonths must be at least 1");
        }
        requireReasonableMagnitude(amount, "amount");
        requireReasonableMagnitude(rebalancingBandPercent, "rebalancingBandPercent");

        Map<String, BigDecimal> weightsPercent = parsePositions(positions);
        var request = new SparplanRequestDto(
                startDate, amount, intervalMonths, weightsPercent,
                rebalancing, rebalancingIntervalMonths, rebalancingMode, rebalancingBandPercent);
        return sparplanSimulationService.simulate(request);
    }

    private Map<String, BigDecimal> parsePositions(String positions) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        for (String part : positions.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) {
                throw new InvalidSimulationParameterException(
                        "Invalid positions entry '" + part + "', expected format SYMBOL:weight");
            }
            BigDecimal weight = new BigDecimal(kv[1].trim());
            requireReasonableMagnitude(weight, "weight for " + kv[0]);
            if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidSimulationParameterException("weight for " + kv[0] + " must be positive");
            }
            weights.put(kv[0].trim().toUpperCase(), weight);
        }
        if (weights.isEmpty()) {
            throw new InvalidSimulationParameterException("No positions given");
        }
        if (weights.size() > MAX_POSITIONS) {
            throw new InvalidSimulationParameterException("At most " + MAX_POSITIONS + " positions are supported");
        }
        return weights;
    }

    private void requireReasonableMagnitude(BigDecimal value, String fieldName) {
        if (value.precision() > MAX_PRECISION || value.scale() > MAX_SCALE || value.scale() < -MAX_SCALE) {
            throw new InvalidSimulationParameterException(fieldName + " is out of the allowed range");
        }
    }
}
