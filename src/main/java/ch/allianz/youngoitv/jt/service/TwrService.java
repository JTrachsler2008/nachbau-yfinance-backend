package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Time-Weighted Return: Verkettung cashflow-bereinigter Teilperiodenrenditen. Reine Funktion ueber
 * eine Liste von Bewertungsperioden, damit sie ohne DB-Fixtures und ohne Live-Kursdaten testbar ist
 * (die Zusammenstellung der Perioden aus der echten Transaktionshistorie erfolgt in der aufrufenden
 * Service-Schicht).
 */
@Service
public class TwrService {

    private static final int SCALE = 10;

    public BigDecimal calculate(List<ValuationPeriod> periods) {
        BigDecimal cumulative = BigDecimal.ONE;
        for (ValuationPeriod period : periods) {
            BigDecimal denominator = period.startValue().add(period.cashFlow());
            if (denominator.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal periodReturn = period.endValue()
                    .subtract(period.startValue())
                    .subtract(period.cashFlow())
                    .divide(denominator, SCALE, RoundingMode.HALF_UP);
            cumulative = cumulative.multiply(BigDecimal.ONE.add(periodReturn));
        }
        return cumulative.subtract(BigDecimal.ONE);
    }
}
