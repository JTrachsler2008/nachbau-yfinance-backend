package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ComparePortfoliosRequestDto(
        @NotNull @Valid PortfolioCompositionDto portfolioA,
        @NotNull @Valid PortfolioCompositionDto portfolioB,
        @Positive @Max(100) Integer periodYears) {
}
