package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Summe der Dividendenerträge über alle DIVIDEND-Transaktionen, konsistent in eine
 * Anzeigewährung umgerechnet.
 *
 * <p>Ohne die Coupons der Anleihen: die stehen im {@link InterestService} und in einer eigenen
 * Kennzahl, damit Zins- und Dividendenertrag getrennt lesbar bleiben.</p>
 *
 * <p>Brutto, also ohne Abzug von Gebühr und Steuer - wie {@code TransactionServiceImpl.applyDividend}
 * dem Konto gutschreibt. Der {@link InterestService} rechnet für Coupons netto, weil dort auch die
 * Kontobuchung netto erfolgt.</p>
 */
@Service
public class DividendsService {

    private final FxConversionService fxConversionService;

    public DividendsService(FxConversionService fxConversionService) {
        this.fxConversionService = fxConversionService;
    }

    public BigDecimal calculateTotalInCurrency(List<Transaction> transactions, String displayCurrency) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if (tx.getTransactionType() != TransactionType.DIVIDEND) {
                continue;
            }
            BigDecimal amount = tx.getPrice().multiply(tx.getQuantity());
            total = total.add(
                    fxConversionService.convert(amount, tx.getTransactionCurrency(), displayCurrency, tx.getTransactionDate()));
        }
        return total;
    }
}
