package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * {@code from}/{@code to} sind unabhängig voneinander optional und haben Vorrang vor
 * {@code periodYears}: fehlt eines der beiden Daten, ergänzt {@code CompareServiceImpl} es
 * (fehlendes {@code to} wird gestern, fehlendes {@code from} wird aus {@code periodYears} bzw.
 * dessen Default 10 Jahren vor {@code to} bestimmt).
 */
public record ComparePortfoliosRequestDto(
        @NotNull @Valid PortfolioCompositionDto portfolioA,
        @NotNull @Valid PortfolioCompositionDto portfolioB,
        @Positive @Max(100) Integer periodYears,
        LocalDate from,
        LocalDate to) {
}
