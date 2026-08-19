package ch.allianz.youngoitv.jt.dto;

import java.util.List;

public record ComparePortfoliosResponseDto(
        String nameA, String nameB, List<PortfolioComparisonPointDto> series) {
}
