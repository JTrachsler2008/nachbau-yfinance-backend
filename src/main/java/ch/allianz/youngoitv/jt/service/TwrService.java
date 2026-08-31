package ch.allianz.youngoitv.jt.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Time-Weighted Return: Verkettung cashflow-bereinigter Teilperiodenrenditen. Reine Funktion über
 * eine Liste von Bewertungsperioden, damit sie ohne DB-Fixtures und ohne Live-Kursdaten testbar ist
 * (die Zusammenstellung der Perioden aus der echten Transaktionshistorie erfolgt in der aufrufenden
 * Service-Schicht).
 */
@Service
public class TwrService {

    private static final int SCALE = 10;

    public BigDecimal calculate(List<ValuationPeriod> periods) {
        List<BigDecimal> factors = chain(periods);
        BigDecimal cumulative = factors.isEmpty() ? BigDecimal.ONE : factors.get(factors.size() - 1);
        return cumulative.subtract(BigDecimal.ONE);
    }

    /**
     * Der Wachstumsfaktor nach jeder Teilperiode, also die Kette selbst statt nur ihres Endwerts.
     *
     * <p>Existiert für den Wertverlauf: eine auf 100 normierte Linie ist derselbe Rechenweg wie die
     * Gesamtrendite, nur an jedem Zwischenpunkt abgelesen. Über {@link #calculate} je Präfix wäre es
     * dieselbe Aussage in quadratischer Laufzeit, und eine zweite Verkettung in der Aufrufschicht
     * wäre eine zweite Implementierung derselben Formel, die auseinanderlaufen kann.</p>
     *
     * @return so viele Faktoren wie Perioden, in derselben Reihenfolge; {@code 1} als Startwert steht
     *     nicht darin, weil ihm keine Periode entspricht
     */
    public List<BigDecimal> chain(List<ValuationPeriod> periods) {
        List<BigDecimal> factors = new ArrayList<>(periods.size());
        BigDecimal cumulative = BigDecimal.ONE;
        for (ValuationPeriod period : periods) {
            cumulative = cumulative.multiply(BigDecimal.ONE.add(periodReturn(period)));
            factors.add(cumulative);
        }
        return factors;
    }

    /**
     * Rendite einer Teilperiode, bereinigt um den Cashflow der Periode.
     *
     * <p>Bezugsgrösse ist das Vermögen zu Periodenbeginn, nicht {@code startValue + cashFlow}. Der
     * Unterschied ist die Annahme darüber, wann der Cashflow eingetroffen ist, und bei einem
     * Tagesraster ist "am Ende der Periode" die richtige: der Schlusskurs des Buchungstags bewertet
     * die gekauften Stücke schon mit, das neue Geld hat also noch keine ganze Periode mitverdient.</p>
     *
     * <p>Der Unterschied ist nicht akademisch. Wer 90 % seines Depots verkauft, hat einen Abfluss in
     * der Grösse seines Vermögens; ein Nenner {@code startValue + cashFlow} wäre dann fast 0 und würde
     * die Tagesrendite um ein Vielfaches aufblasen, bevor die Kette sie weiterträgt. Ein
     * Vollverkauf ergäbe einen negativen Nenner und fiele ganz aus der Messung.</p>
     *
     * <p>Eine Ausnahme braucht es doch: war zu Beginn kein Kapital da, ist der Zufluss selbst die
     * Bezugsgrösse - sonst wäre die erste Anlage überhaupt eine Division durch 0. Gemessen wird dann
     * die Entwicklung vom Kaufpreis bis zum Schlusskurs des Kauftags. Bleibt auch das nicht positiv
     * (leeres Portfolio, kein Zufluss), trägt die Periode den Faktor 1 und lässt die Kette unverändert:
     * ohne Kapital gibt es keine Rendite, und 0 % wäre eine Aussage über nichts.</p>
     */
    private BigDecimal periodReturn(ValuationPeriod period) {
        BigDecimal base = period.startValue().compareTo(BigDecimal.ZERO) > 0
                ? period.startValue()
                : period.cashFlow();
        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return period.endValue()
                .subtract(period.startValue())
                .subtract(period.cashFlow())
                .divide(base, SCALE, RoundingMode.HALF_UP);
    }
}
