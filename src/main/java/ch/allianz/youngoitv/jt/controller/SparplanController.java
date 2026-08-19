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
            weights.put(kv[0].trim().toUpperCase(), new BigDecimal(kv[1].trim()));
        }
        if (weights.isEmpty()) {
            throw new InvalidSimulationParameterException("No positions given");
        }
        return weights;
    }
}
