package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;

public record PortfolioCreateRequestDto(
        @NotBlank String name,
        @NotBlank String baseCurrency,
        String description) {
}
