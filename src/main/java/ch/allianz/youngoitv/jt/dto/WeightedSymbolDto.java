package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record WeightedSymbolDto(@NotBlank String symbol, @Positive BigDecimal weight) {
}
