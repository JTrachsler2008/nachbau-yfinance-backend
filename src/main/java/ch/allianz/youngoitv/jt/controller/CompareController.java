package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.AssetClassComparisonResponseDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosRequestDto;
import ch.allianz.youngoitv.jt.dto.ComparePortfoliosResponseDto;
import ch.allianz.youngoitv.jt.service.CompareService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/compare")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/asset-classes")
    public AssetClassComparisonResponseDto assetClasses(@RequestParam(defaultValue = "10") int period) {
        return compareService.getAssetClassComparison(period);
    }

    @PostMapping("/portfolios")
    public ComparePortfoliosResponseDto portfolios(@Valid @RequestBody ComparePortfoliosRequestDto request) {
        return compareService.comparePortfolios(request);
    }
}
