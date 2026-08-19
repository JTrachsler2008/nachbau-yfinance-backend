package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequestDto(
        @NotNull Long securityId,
        @NotNull TransactionType transactionType,
        @NotNull @PositiveOrZero BigDecimal quantity,
        @Positive BigDecimal price,
        @PositiveOrZero BigDecimal fee,
        @PositiveOrZero BigDecimal tax,
        @Positive BigDecimal splitRatio,
        @NotNull String transactionCurrency,
        @NotNull LocalDate transactionDate) {
}
