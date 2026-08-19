package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SparplanChartPointDto(LocalDate month, BigDecimal portfolioValue, BigDecimal invested) {
}
