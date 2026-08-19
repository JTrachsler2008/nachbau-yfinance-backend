package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.FxRateCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.FxRate;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.repository.FxRateRepository;
import ch.allianz.youngoitv.jt.service.FxRateService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository fxRateRepository;

    public FxRateServiceImpl(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    @Override
    public FxRate create(FxRateCreateRequestDto request) {
        FxRate fxRate = new FxRate();
        fxRate.setBaseCurrency(request.baseCurrency());
        fxRate.setQuoteCurrency(request.quoteCurrency());
        fxRate.setRateDate(request.rateDate());
        fxRate.setRate(request.rate());
        fxRate.setCreatedAt(LocalDateTime.now());
        return fxRateRepository.save(fxRate);
    }

    @Override
    public FxRate getLatestOnOrBeforeOrThrow(String baseCurrency, String quoteCurrency, LocalDate onOrBefore) {
        return fxRateRepository.findLatestOnOrBefore(baseCurrency, quoteCurrency, onOrBefore)
                .orElseThrow(() -> new FxRateNotAvailableException(
                        "No FX rate available for " + baseCurrency + "/" + quoteCurrency
                                + " on or before " + onOrBefore));
    }
}
