package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.UserRole;
import java.time.LocalDateTime;

public record UserResponseDto(Long id, String username, String email, UserRole role, LocalDateTime createdAt) {
}
