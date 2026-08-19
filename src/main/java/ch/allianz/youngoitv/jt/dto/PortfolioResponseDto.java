package ch.allianz.youngoitv.jt.dto;

import java.time.LocalDateTime;

public record PortfolioResponseDto(
        Long id,
        String name,
        String baseCurrency,
        String description,
        Long managerUserId,
        String managerUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
