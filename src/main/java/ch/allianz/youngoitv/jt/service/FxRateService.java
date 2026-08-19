package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.FxRateCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.FxRate;
import java.time.LocalDate;

public interface FxRateService {

    FxRate create(FxRateCreateRequestDto request);

    FxRate getLatestOnOrBeforeOrThrow(String baseCurrency, String quoteCurrency, LocalDate onOrBefore);
}
