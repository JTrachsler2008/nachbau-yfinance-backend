package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Realisierte Gewinne ueber SELL-Transaktionen hinweg, konsistent in eine Anzeigewaehrung
 * umgerechnet (behebt die im Original fehlende FX-Konvertierung: dort werden realisierte Gewinne
 * unterschiedlicher Handelswaehrungen unkonvertiert aufsummiert).
 */
@Service
public class RealizedGainsService {

    private final FifoLotService fifoLotService;
    private final FxConversionService fxConversionService;

    public RealizedGainsService(FifoLotService fifoLotService, FxConversionService fxConversionService) {
        this.fifoLotService = fifoLotService;
        this.fxConversionService = fxConversionService;
    }

    public BigDecimal calculateTotalInCurrency(List<Transaction> transactionsOrderedByDate, String displayCurrency) {
        BigDecimal total = BigDecimal.ZERO;
        for (RealizedGain gain : fifoLotService.calculateRealizedGains(transactionsOrderedByDate)) {
            total = total.add(fxConversionService.convert(gain.amount(), gain.currency(), displayCurrency, gain.date()));
        }
        return total;
    }
}
