package ch.allianz.youngoitv.jt.util;

import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Ein aufgelöster Auswertungszeitraum {@code [from, to]}.
 *
 * <p>Zwei Wege führen hierher: entweder {@code lookbackDays} als Kalendertage vor gestern (für die
 * Presets der Oberfläche) oder ein freies {@code from}/{@code to}. Die Auflösung sitzt in der
 * Controller-Schicht, die Dienste darunter kennen nur noch das fertige Intervall.</p>
 *
 * <p>Eigene Klasse und keine Methode im Controller, weil inzwischen zwei Endpunkte
 * ({@code /risk} und {@code /history}) dieselbe Wahl treffen. Zwei Kopien dieser Prüfungen wären
 * zwei Stellen, an denen sich die Grenzen unbemerkt auseinanderentwickeln - und die Oberfläche
 * bedient beide Endpunkte mit demselben Bedienelement.</p>
 */
public record DateRange(LocalDate from, LocalDate to) {

    /**
     * Untergrenze der Spanne. Kommt von den Risikokennzahlen, die aus weniger als 20 Handelstagen
     * keine belastbare Jahresaussage machen; für den Wertverlauf ist sie kein Hindernis, weil eine
     * Linie über weniger als einen Monat ohnehin keine Frage beantwortet.
     */
    public static final int MIN_DAYS = 30;

    /** Obergrenze: zehn Jahre. Darüber liefert der Marktdatenanbieter ohnehin lückenhaft. */
    public static final int MAX_DAYS = 3650;

    /**
     * Löst die Parameter eines Aufrufs zu einem Intervall auf.
     *
     * <p>Sind {@code from} und {@code to} gesetzt, haben sie Vorrang. Nur eines von beiden anzugeben,
     * ist keine gültige Wahl zwischen den beiden Wegen, sondern ein unvollständiger Aufruf.</p>
     */
    public static DateRange resolve(int lookbackDays, LocalDate from, LocalDate to) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (from == null && to == null) {
            if (lookbackDays < MIN_DAYS || lookbackDays > MAX_DAYS) {
                throw new InvalidSimulationParameterException(
                        "lookbackDays must be between " + MIN_DAYS + " and " + MAX_DAYS);
            }
            return new DateRange(yesterday.minusDays(lookbackDays), yesterday);
        }
        if (from == null || to == null) {
            throw new InvalidSimulationParameterException("from and to must both be given for a custom range");
        }
        if (to.isAfter(yesterday)) {
            throw new InvalidSimulationParameterException("to must not be after yesterday");
        }
        if (!from.isBefore(to)) {
            throw new InvalidSimulationParameterException("from must be before to");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new InvalidSimulationParameterException(
                    "the range between from and to must be between " + MIN_DAYS + " and " + MAX_DAYS + " days");
        }
        return new DateRange(from, to);
    }
}
