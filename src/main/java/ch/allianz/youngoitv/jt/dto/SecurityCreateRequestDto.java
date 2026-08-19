package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SecurityCreateRequestDto(
        @NotBlank String symbol,
        String isin,
        @NotBlank String name,
        @NotBlank String assetType,
        String exchangeCode,
        @NotBlank String tradingCurrency,
        String countryCode,
        String sector,
        BigDecimal couponRate,
        LocalDate maturityDate) {
}
