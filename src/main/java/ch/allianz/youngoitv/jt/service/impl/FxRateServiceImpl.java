package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.FxRateCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.FxRate;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.repository.FxRateRepository;
import ch.allianz.youngoitv.jt.security.AdminCheckService;
import ch.allianz.youngoitv.jt.service.FxRateService;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FxRateServiceImpl implements FxRateService {

    private final FxRateRepository fxRateRepository;
    private final AdminCheckService adminCheckService;
    private final PriceLookupService priceLookupService;

    public FxRateServiceImpl(
            FxRateRepository fxRateRepository,
            AdminCheckService adminCheckService,
            PriceLookupService priceLookupService) {
        this.fxRateRepository = fxRateRepository;
        this.adminCheckService = adminCheckService;
        this.priceLookupService = priceLookupService;
    }

    @Override
    public FxRate create(FxRateCreateRequestDto request, String requesterUsername) {
        adminCheckService.requireAdmin(requesterUsername);
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
                .or(() -> fetchAndStoreLiveRate(baseCurrency, quoteCurrency, onOrBefore))
                .orElseThrow(() -> new FxRateNotAvailableException(
                        "No FX rate available for " + baseCurrency + "/" + quoteCurrency
                                + " on or before " + onOrBefore));
    }

    /**
     * Ein in fx_rates fehlender Kurs heisst nicht "kein Kurs existiert", sondern nur "noch nie
     * gebraucht" - vor dem ersten Zugriff auf ein Währungspaar ist die Tabelle dafür leer. Statt das
     * dem Aufrufer als Fehler zu melden, nutzt das denselben "nächstgelegener Kurs auf-oder-vor
     * Datum"-Lookup, den auch Wertpapierkurse verwenden ({@link PriceLookupService}), über das
     * Yahoo-Kürzel {@code <BASE><QUOTE>=X}, und speichert den gefundenen Kurs für künftige Aufrufe.
     * Liefert auch der Marktdatenanbieter nichts (z.B. ein exotisches Paar), bleibt es bei der
     * Exception statt einer Schätzung.
     */
    private Optional<FxRate> fetchAndStoreLiveRate(String baseCurrency, String quoteCurrency, LocalDate onOrBefore) {
        String fxSymbol = baseCurrency + quoteCurrency + "=X";
        return priceLookupService.findPriceAtOrBefore(fxSymbol, onOrBefore)
                .map(price -> {
                    FxRate fxRate = new FxRate();
                    fxRate.setBaseCurrency(baseCurrency);
                    fxRate.setQuoteCurrency(quoteCurrency);
                    fxRate.setRateDate(onOrBefore);
                    fxRate.setRate(price);
                    fxRate.setCreatedAt(LocalDateTime.now());
                    return fxRateRepository.save(fxRate);
                });
    }
}
