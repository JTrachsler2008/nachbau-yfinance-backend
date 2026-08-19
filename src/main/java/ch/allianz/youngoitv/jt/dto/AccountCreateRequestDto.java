package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountCreateRequestDto(@NotBlank String name, @NotBlank String currency) {
}
