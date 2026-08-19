package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequestDto(
        @NotNull Long securityId,
        @NotNull TransactionType transactionType,
        @NotNull BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal splitRatio,
        @NotNull String transactionCurrency,
        @NotNull LocalDate transactionDate) {
}
