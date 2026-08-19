package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.service.RebalancingMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record SparplanRequestDto(
        LocalDate startDate,
        BigDecimal amount,
        int intervalMonths,
        Map<String, BigDecimal> weightsPercent,
        boolean rebalancing,
        int rebalancingIntervalMonths,
        RebalancingMode rebalancingMode,
        BigDecimal rebalancingBandPercent) {
}
