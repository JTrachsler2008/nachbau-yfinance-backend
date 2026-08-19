package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRateCreateRequestDto(
        @NotBlank String baseCurrency,
        @NotBlank String quoteCurrency,
        @NotNull LocalDate rateDate,
        @NotNull @Positive BigDecimal rate) {
}
