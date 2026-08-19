package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashMovementRequestDto(@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount) {
}
