package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestResponseDto(
        String symbol,
        LocalDate buyDate,
        BigDecimal quantity,
        BigDecimal priceAtBuy,
        BigDecimal currentPrice,
        BigDecimal investedAmount,
        BigDecimal currentValue,
        BigDecimal gainLoss,
        BigDecimal returnPercent,
        List<BacktestChartPointDto> priceHistory) {
}
