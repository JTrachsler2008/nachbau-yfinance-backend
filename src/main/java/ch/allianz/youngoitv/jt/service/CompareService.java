package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;
import java.time.LocalDate;

public interface CompareService {

    /**
     * Die Auflösung von "Preset in Jahren" oder freiem Zeitraum in {@code from}/{@code to} ist Sache
     * des Controllers, siehe {@code CompareController.resolveRange}.
     */
    AssetClassComparisonResponseDto getAssetClassComparison(LocalDate from, LocalDate to);

    ComparePortfoliosResponseDto comparePortfolios(ComparePortfoliosRequestDto request);
}
