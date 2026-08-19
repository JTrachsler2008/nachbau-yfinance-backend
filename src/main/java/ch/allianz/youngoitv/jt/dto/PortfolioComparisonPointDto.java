package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioComparisonPointDto(LocalDate date, BigDecimal portfolioAValue, BigDecimal portfolioBValue) {
}
