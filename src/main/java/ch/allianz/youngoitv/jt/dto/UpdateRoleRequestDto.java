package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequestDto(@NotNull UserRole role) {
}
