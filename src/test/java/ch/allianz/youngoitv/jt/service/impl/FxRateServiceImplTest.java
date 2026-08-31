package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.entity.FxRate;
import ch.allianz.youngoitv.jt.exception.FxRateNotAvailableException;
import ch.allianz.youngoitv.jt.repository.FxRateRepository;
import ch.allianz.youngoitv.jt.security.AdminCheckService;
import ch.allianz.youngoitv.jt.util.PriceLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxRateServiceImplTest {

    @Mock
    private FxRateRepository fxRateRepository;
    @Mock
    private AdminCheckService adminCheckService;
    @Mock
    private PriceLookupService priceLookupService;

    @Test
    void returnsTheCachedRateWithoutAskingTheMarketDataProvider() {
        FxRateServiceImpl service = new FxRateServiceImpl(fxRateRepository, adminCheckService, priceLookupService);
        FxRate cached = new FxRate();
        cached.setRate(new BigDecimal("0.91"));
        when(fxRateRepository.findLatestOnOrBefore("USD", "CHF", LocalDate.of(2026, 1, 5)))
                .thenReturn(Optional.of(cached));

        FxRate result = service.getLatestOnOrBeforeOrThrow("USD", "CHF", LocalDate.of(2026, 1, 5));

        assertThat(result.getRate()).isEqualByComparingTo("0.91");
        verifyNoInteractions(priceLookupService);
    }

    /**
     * Ein fehlender Kurs in fx_rates heisst nicht "kein Kurs existiert", sondern nur "noch nie
     * gebraucht": vor dem ersten Zugriff auf ein Währungspaar ist die Tabelle dafür leer.
     */
    @Test
    void fetchesFromThePriceLookupServiceAndCachesItWhenMissingInTheDatabase() {
        FxRateServiceImpl service = new FxRateServiceImpl(fxRateRepository, adminCheckService, priceLookupService);
        when(fxRateRepository.findLatestOnOrBefore("USD", "CHF", LocalDate.of(2026, 1, 5)))
                .thenReturn(Optional.empty());
        when(priceLookupService.findPriceAtOrBefore("USDCHF=X", LocalDate.of(2026, 1, 5)))
                .thenReturn(Optional.of(new BigDecimal("0.87")));
        when(fxRateRepository.save(any(FxRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FxRate result = service.getLatestOnOrBeforeOrThrow("USD", "CHF", LocalDate.of(2026, 1, 5));

        assertThat(result.getRate()).isEqualByComparingTo("0.87");
        ArgumentCaptor<FxRate> captor = ArgumentCaptor.forClass(FxRate.class);
        verify(fxRateRepository).save(captor.capture());
        assertThat(captor.getValue().getBaseCurrency()).isEqualTo("USD");
        assertThat(captor.getValue().getQuoteCurrency()).isEqualTo("CHF");
        assertThat(captor.getValue().getRateDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void throwsWhenNeitherTheDatabaseNorThePriceLookupServiceHaveARate() {
        FxRateServiceImpl service = new FxRateServiceImpl(fxRateRepository, adminCheckService, priceLookupService);
        when(fxRateRepository.findLatestOnOrBefore("EUR", "JPY", LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.empty());
        when(priceLookupService.findPriceAtOrBefore("EURJPY=X", LocalDate.of(2026, 1, 1)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestOnOrBeforeOrThrow("EUR", "JPY", LocalDate.of(2026, 1, 1)))
                .isInstanceOf(FxRateNotAvailableException.class);
    }
}
