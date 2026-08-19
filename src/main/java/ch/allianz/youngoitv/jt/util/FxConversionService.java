package ch.allianz.youngoitv.jt.util;

import ch.allianz.youngoitv.jt.service.FxRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Zentrale FX-Umrechnung, ersetzt die im Original mind. 5-fach duplizierte Implementierung
 * (ARC-4, KONV-7). Der Kurs-Lookup selbst (inkl. "kein Kurs vorhanden"-Fehler) lebt in
 * {@link FxRateService}, damit es nur eine Implementierung dieser Logik gibt.
 */
@Service
public class FxConversionService {

    private final FxRateService fxRateService;

    public FxConversionService(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        return amount.multiply(getRate(fromCurrency, toCurrency, date));
    }

    public BigDecimal getRate(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        return fxRateService.getLatestOnOrBeforeOrThrow(fromCurrency, toCurrency, date).getRate();
    }
}
