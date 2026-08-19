package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseSimulationResponseDto(
        String symbol,
        String securityName,
        BigDecimal currentPrice,
        BigDecimal quantity,
        BigDecimal cost,
        BigDecimal currentPortfolioValue,
        BigDecimal simulatedPortfolioValue,
        BigDecimal valueChange,
        BigDecimal returnChangePercent,
        List<WeightItemDto> currentWeights,
        List<WeightItemDto> simulatedWeights) {
}
