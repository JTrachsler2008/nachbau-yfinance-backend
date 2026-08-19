package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

public record WeightItemDto(String symbol, BigDecimal value, BigDecimal percent) {
}
