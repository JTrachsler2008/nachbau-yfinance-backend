package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SecurityResponseDto(
        Long id,
        String symbol,
        String isin,
        String name,
        String assetType,
        String exchangeCode,
        String tradingCurrency,
        String countryCode,
        String sector,
        BigDecimal couponRate,
        LocalDate maturityDate) {
}
