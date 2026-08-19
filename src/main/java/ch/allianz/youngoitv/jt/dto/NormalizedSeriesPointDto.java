package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record NormalizedSeriesPointDto(LocalDate date, Map<String, BigDecimal> valuesBySymbol) {
}
