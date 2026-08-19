package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LotResponseDto(BigDecimal quantity, BigDecimal purchasePrice, LocalDate purchaseDate) {
}
