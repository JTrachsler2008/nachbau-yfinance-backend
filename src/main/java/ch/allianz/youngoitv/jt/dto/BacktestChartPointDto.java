package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestChartPointDto(LocalDate date, BigDecimal price, BigDecimal portfolioValue) {
}
