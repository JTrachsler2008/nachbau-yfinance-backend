package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Money-Weighted Return: interner Zinsfuss (IRR) ueber alle Cashflows, geloest per
 * Bisektionsverfahren (Grenzen -0.9999 bis 50.0, 200 Iterationen). Reine Funktion ueber eine
 * uebergebene Cashflow-Liste (chronologisch sortiert), damit sie ohne DB-Fixtures testbar ist.
 */
@Service
public class MwrService {

    private static final BigDecimal LOWER_BOUND = new BigDecimal("-0.9999");
    private static final BigDecimal UPPER_BOUND = new BigDecimal("50.0");
    private static final int ITERATIONS = 200;
    private static final MathContext MATH_CONTEXT = new MathContext(12);

    public BigDecimal calculate(List<CashFlow> cashFlows) {
        if (cashFlows.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal low = LOWER_BOUND;
        BigDecimal high = UPPER_BOUND;
        BigDecimal mid = BigDecimal.ZERO;

        for (int i = 0; i < ITERATIONS; i++) {
            mid = low.add(high).divide(BigDecimal.TWO, MATH_CONTEXT);
            BigDecimal npv = netPresentValue(cashFlows, mid);
            if (npv.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return mid.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal netPresentValue(List<CashFlow> cashFlows, BigDecimal rate) {
        var firstDate = cashFlows.get(0).date();
        BigDecimal onePlusRate = BigDecimal.ONE.add(rate);
        BigDecimal npv = BigDecimal.ZERO;
        for (CashFlow cashFlow : cashFlows) {
            long days = ChronoUnit.DAYS.between(firstDate, cashFlow.date());
            double years = days / 365.0;
            double discountFactor = Math.pow(onePlusRate.doubleValue(), years);
            npv = npv.add(cashFlow.amount().divide(BigDecimal.valueOf(discountFactor), MATH_CONTEXT));
        }
        return npv;
    }
}
