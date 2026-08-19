package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRateResponseDto(Long id, String baseCurrency, String quoteCurrency, LocalDate rateDate, BigDecimal rate) {
}
