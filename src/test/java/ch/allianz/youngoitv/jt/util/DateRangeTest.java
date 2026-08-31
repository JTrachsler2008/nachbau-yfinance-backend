package ch.allianz.youngoitv.jt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Der Zeitraum wird von zwei Endpunkten aufgelöst ({@code /risk} und {@code /history}), deshalb liegt
 * die Regel an einer Stelle und wird auch nur an einer Stelle geprüft.
 */
class DateRangeTest {

    @Test
    void lookbackDaysEndYesterdayAndReachBack() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        DateRange range = DateRange.resolve(90, null, null);

        assertThat(range.to()).isEqualTo(yesterday);
        assertThat(range.from()).isEqualTo(yesterday.minusDays(90));
    }

    /** Gestern und nicht heute: der Schlusskurs des laufenden Tages steht noch nicht fest. */
    @Test
    void aRangeEndingTodayIsRejected() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> DateRange.resolve(365, today.minusDays(200), today))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("yesterday");
    }

    @Test
    void explicitDatesTakePrecedenceOverLookbackDays() {
        LocalDate from = LocalDate.of(2024, 1, 2);
        LocalDate to = LocalDate.of(2024, 6, 28);

        DateRange range = DateRange.resolve(90, from, to);

        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
    }

    /** Nur eine der beiden Grenzen ist keine halbe Angabe, sondern eine unklare. */
    @Test
    void onlyOneOfTheTwoDatesIsRejected() {
        assertThatThrownBy(() -> DateRange.resolve(365, LocalDate.of(2024, 1, 2), null))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("both");
    }

    @Test
    void aReversedRangeIsRejected() {
        assertThatThrownBy(() -> DateRange.resolve(365, LocalDate.of(2024, 6, 28), LocalDate.of(2024, 1, 2)))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("before");
    }

    @Test
    void aTooShortLookbackIsRejected() {
        assertThatThrownBy(() -> DateRange.resolve(DateRange.MIN_DAYS - 1, null, null))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("lookbackDays");
    }

    @Test
    void aTooLongLookbackIsRejected() {
        assertThatThrownBy(() -> DateRange.resolve(DateRange.MAX_DAYS + 1, null, null))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("lookbackDays");
    }

    @Test
    void aTooShortExplicitRangeIsRejected() {
        LocalDate from = LocalDate.of(2024, 1, 2);

        assertThatThrownBy(() -> DateRange.resolve(365, from, from.plusDays(DateRange.MIN_DAYS - 1)))
                .isInstanceOf(InvalidSimulationParameterException.class)
                .hasMessageContaining("between from and to");
    }

    @Test
    void theBoundsThemselvesAreAccepted() {
        LocalDate from = LocalDate.of(2020, 1, 2);

        assertThat(DateRange.resolve(DateRange.MIN_DAYS, null, null)).isNotNull();
        assertThat(DateRange.resolve(DateRange.MAX_DAYS, null, null)).isNotNull();
        assertThat(DateRange.resolve(365, from, from.plusDays(DateRange.MIN_DAYS))).isNotNull();
    }
}
