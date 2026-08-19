package ch.allianz.youngoitv.jt.dto;

import java.util.List;

public record AssetClassComparisonResponseDto(
        List<AssetClassDefinitionDto> assetClasses, List<NormalizedSeriesPointDto> series) {
}
