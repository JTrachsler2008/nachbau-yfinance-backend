package ch.allianz.youngoitv.jt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.entity.FxRate;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxConversionServiceTest {

    @Mock
    private FxRateRepository fxRateRepository;

    @Test
    void sameCurrencyReturnsOriginalAmountWithoutDbLookup() {
        FxConversionService service = new FxConversionService(fxRateRepository);

        BigDecimal result = service.convert(new BigDecimal("100.00"), "CHF", "CHF", LocalDate.of(2026, 1, 1));

        assertThat(result).isEqualByComparingTo("100.00");
        verifyNoInteractions(fxRateRepository);
    }

    @Test
    void convertsUsingLatestRateOnOrBeforeDate() {
        FxConversionService service = new FxConversionService(fxRateRepository);
        FxRate rate = new FxRate();
        rate.setRate(new BigDecimal("0.9"));
        when(fxRateRepository.findLatestOnOrBefore("USD", "CHF", LocalDate.of(2026, 1, 5)))
                .thenReturn(Optional.of(rate));

        BigDecimal result = service.convert(new BigDecimal("100"), "USD", "CHF", LocalDate.of(2026, 1, 5));

        assertThat(result).isEqualByComparingTo("90.0");
    }

    @Test
    void missingRateThrowsInsteadOfSilentlyAssumingOneToOne() {
        FxConversionService service = new FxConversionService(fxRateRepository);
        when(fxRateRepository.findLatestOnOrBefore("EUR", "JPY", LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert(BigDecimal.TEN, "EUR", "JPY", LocalDate.of(2026, 1, 1)))
                .isInstanceOf(FxRateNotAvailableException.class);
    }
}
