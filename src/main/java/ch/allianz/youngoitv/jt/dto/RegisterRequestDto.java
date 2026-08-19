package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password) {
}
