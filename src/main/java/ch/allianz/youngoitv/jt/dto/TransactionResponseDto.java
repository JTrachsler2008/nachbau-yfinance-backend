package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponseDto(
        Long id,
        Long securityId,
        TransactionType transactionType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal splitRatio,
        String transactionCurrency,
        BigDecimal fxRateToPortfolio,
        LocalDate transactionDate) {
}
