package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

public record CurrencyAmountResponseDto(BigDecimal amount, String currency) {
}
