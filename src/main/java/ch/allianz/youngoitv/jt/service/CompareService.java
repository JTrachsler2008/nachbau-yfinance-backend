package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;

public interface CompareService {

    AssetClassComparisonResponseDto getAssetClassComparison(int periodYears);

    ComparePortfoliosResponseDto comparePortfolios(ComparePortfoliosRequestDto request);
}
