package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.FxRateCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.FxRateResponseDto;
import ch.allianz.youngoitv.jt.mapper.FxRateMapper;
import ch.allianz.youngoitv.jt.service.FxRateService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fx-rates")
public class FxRateController {

    private final FxRateService fxRateService;
    private final FxRateMapper fxRateMapper;

    public FxRateController(FxRateService fxRateService, FxRateMapper fxRateMapper) {
        this.fxRateService = fxRateService;
        this.fxRateMapper = fxRateMapper;
    }

    @PostMapping
    public ResponseEntity<FxRateResponseDto> create(@Valid @RequestBody FxRateCreateRequestDto request) {
        var fxRate = fxRateService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(fxRateMapper.toResponseDto(fxRate));
    }

    @GetMapping
    public FxRateResponseDto findLatest(
            @RequestParam String base,
            @RequestParam String quote,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return fxRateMapper.toResponseDto(fxRateService.getLatestOnOrBeforeOrThrow(base, quote, date));
    }
}
