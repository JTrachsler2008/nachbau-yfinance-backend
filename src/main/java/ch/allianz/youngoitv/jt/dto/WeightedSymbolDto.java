package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record WeightedSymbolDto(
        @NotBlank String symbol, @Positive @Digits(integer = 15, fraction = 6) BigDecimal weight) {
}
