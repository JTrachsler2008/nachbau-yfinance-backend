package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Summe der Zinserträge über alle COUPON-Transaktionen, konsistent in eine Anzeigewährung
 * umgerechnet.
 *
 * <p>Getrennt vom {@link DividendsService} und nicht als zweite Zahl darin: Zins und Dividende sind
 * zwei Erträge mit verschiedener Herkunft und in vielen Ländern verschiedener Besteuerung. In einer
 * gemeinsamen Summe liesse sich der Anleiheertrag nachträglich nicht mehr herausrechnen.</p>
 *
 * <p>Netto, also {@code price*quantity - fee - tax}, genau der Betrag, den
 * {@code TransactionServiceImpl.applyCoupon} dem Konto gutschreibt. Der {@link DividendsService}
 * rechnet dagegen brutto; die beiden Summen sind deshalb nicht nach derselben Regel gebildet, und
 * jede von ihnen folgt der Kontowirkung ihres Buchungstyps.</p>
 */
@Service
public class InterestService {

    private final FxConversionService fxConversionService;

    public InterestService(FxConversionService fxConversionService) {
        this.fxConversionService = fxConversionService;
    }

    public BigDecimal calculateTotalInCurrency(List<Transaction> transactions, String displayCurrency) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if (tx.getTransactionType() != TransactionType.COUPON) {
                continue;
            }
            BigDecimal fee = tx.getFee() == null ? BigDecimal.ZERO : tx.getFee();
            BigDecimal tax = tx.getTax() == null ? BigDecimal.ZERO : tx.getTax();
            BigDecimal amount = tx.getPrice().multiply(tx.getQuantity()).subtract(fee).subtract(tax);
            total = total.add(fxConversionService.convert(
                    amount, tx.getTransactionCurrency(), displayCurrency, tx.getTransactionDate()));
        }
        return total;
    }
}
