package ch.allianz.youngoitv.jt.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningsData(String symbol, LocalDate reportDate, BigDecimal epsActual, BigDecimal epsEstimate) {
}
