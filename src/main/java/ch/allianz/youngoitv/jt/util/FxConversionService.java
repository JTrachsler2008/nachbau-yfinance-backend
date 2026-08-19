package ch.allianz.youngoitv.jt.util;

import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Zentrale FX-Umrechnung, ersetzt die im Original mind. 5-fach duplizierte Implementierung
 * (ARC-4, KONV-7). Bei fehlendem Kurs wird eine Exception geworfen statt stillschweigend 1.0
 * anzunehmen (Verbesserung gegenueber dem Original).
 */
@Service
public class FxConversionService {

    private final FxRateRepository fxRateRepository;

    public FxConversionService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        var fxRate = fxRateRepository.findLatestOnOrBefore(fromCurrency, toCurrency, date)
                .orElseThrow(() -> new FxRateNotAvailableException(
                        "No FX rate available for " + fromCurrency + "/" + toCurrency + " on or before " + date));

        return amount.multiply(fxRate.getRate());
    }
}
