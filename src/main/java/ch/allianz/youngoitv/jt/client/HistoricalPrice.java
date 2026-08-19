package ch.allianz.youngoitv.jt.client;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPrice(LocalDate date, BigDecimal close) {
}
