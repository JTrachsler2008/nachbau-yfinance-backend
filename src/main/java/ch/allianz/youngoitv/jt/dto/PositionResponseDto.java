package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;

public record PositionResponseDto(Long id, Long securityId, BigDecimal totalQuantity, BigDecimal averagePurchasePrice) {
}
