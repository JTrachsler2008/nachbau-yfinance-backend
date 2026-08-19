package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.service.RebalancingMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SparplanResponseDto(
        List<SparplanChartPointDto> chartData,
        BigDecimal endValue,
        BigDecimal invested,
        BigDecimal gain,
        BigDecimal totalReturnPercent,
        BigDecimal cagrPercent,
        BigDecimal maxDrawdownPercent,
        boolean rebalancing,
        RebalancingMode rebalancingMode,
        BigDecimal rebalancingBandPercent,
        int rebalancingCount,
        List<RebalancingEventDto> rebalancingEvents,
        Map<String, BigDecimal> targetAllocationPercent,
        Map<String, BigDecimal> currentAllocationPercent) {
}
