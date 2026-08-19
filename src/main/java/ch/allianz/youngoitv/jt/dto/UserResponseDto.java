package ch.allianz.youngoitv.jt.dto;

import java.time.LocalDateTime;

public record UserResponseDto(Long id, String username, String email, LocalDateTime createdAt) {
}
