package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

public record AccountResponseDto(Long id, String name, String currency, BigDecimal cashAmount) {
}
