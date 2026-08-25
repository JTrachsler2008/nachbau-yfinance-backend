package ch.allianz.youngoitv.jt.dto;

import java.time.LocalDateTime;

/**
 * Portfolio für den Client.
 *
 * <p>{@code ownerUsername} steht hier, damit ein Portfolio-Manager in seiner Mandatsliste sieht, wem
 * ein betreutes Portfolio gehört. Ohne diese Angabe stünden dort nur Namen wie "Altersvorsorge", die
 * ein Manager nicht von seinen eigenen unterscheiden könnte (YOUNGOITV-459).</p>
 */
public record PortfolioResponseDto(
        Long id,
        String name,
        String baseCurrency,
        String description,
        String ownerUsername,
        Long managerUserId,
        String managerUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
