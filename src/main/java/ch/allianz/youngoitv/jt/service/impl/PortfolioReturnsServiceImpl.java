package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.PortfolioReturnsResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioValuationResponseDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.service.CashFlow;
import ch.allianz.youngoitv.jt.service.MwrService;
import ch.allianz.youngoitv.jt.service.PortfolioReturnsService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PortfolioValuationService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import ch.allianz.youngoitv.jt.util.FxConversionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Siehe {@link PortfolioReturnsService}.
 *
 * <p>Die Cashflows entstehen aus der tatsächlichen Kontowirkung jeder Buchung
 * ({@code TransactionServiceImpl.applyBuy/applySell/applyDividend}), nicht aus einer neu erfundenen
 * Fachlogik: ein Kauf kostet {@code price*quantity + fee + tax}, ein Verkauf bringt
 * {@code price*quantity - fee - tax}, eine Dividende {@code price*quantity}. SPLIT, ACQUISITION und
 * MERGER rühren den Kontostand laut {@code TransactionServiceImpl} nicht an und tragen deshalb nichts
 * bei.</p>
 */
@Service
public class PortfolioReturnsServiceImpl implements PortfolioReturnsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RESULT_SCALE = 2;

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final PortfolioValuationService portfolioValuationService;
    private final FxConversionService fxConversionService;
    private final MwrService mwrService;

    public PortfolioReturnsServiceImpl(
            PortfolioService portfolioService,
            TransactionService transactionService,
            PortfolioValuationService portfolioValuationService,
            FxConversionService fxConversionService,
            MwrService mwrService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.portfolioValuationService = portfolioValuationService;
        this.fxConversionService = fxConversionService;
        this.mwrService = mwrService;
    }

    @Override
    public PortfolioReturnsResponseDto returns(Long portfolioId, String username) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);
        // Bereits aufsteigend nach Datum sortiert (findByAccountPortfolioIdOrderByTransactionDateAsc),
        // der Zinsfuss braucht diese Reihenfolge nicht selbst herzustellen.
        List<Transaction> transactions = transactionService.getTransactionsForPortfolio(portfolioId);

        List<CashFlow> cashFlows = new ArrayList<>();
        for (Transaction transaction : transactions) {
            BigDecimal signedAmount = signedCashFlowAmount(transaction);
            if (signedAmount == null) {
                continue;
            }
            BigDecimal converted = fxConversionService.convert(
                    signedAmount, transaction.getTransactionCurrency(), portfolio.getBaseCurrency(),
                    transaction.getTransactionDate());
            cashFlows.add(new CashFlow(transaction.getTransactionDate(), converted));
        }

        PortfolioValuationResponseDto valuation = portfolioValuationService.currentValuation(portfolioId, username);
        if (!cashFlows.isEmpty() && valuation.marketValue() != null) {
            cashFlows.add(new CashFlow(LocalDate.now(), valuation.marketValue()));
        }

        // Ohne mindestens zwei Cashflows (einer hinein, der Endwert heraus) hat ein Zinsfuss keine
        // Bedeutung - ein einziger Cashflow liesse sich mit jeder beliebigen Rendite "erklären".
        BigDecimal moneyWeightedReturn =
                cashFlows.size() < 2 ? null : asPercent(mwrService.calculate(cashFlows));

        return new PortfolioReturnsResponseDto(
                portfolio.getId(), portfolio.getBaseCurrency(), null, moneyWeightedReturn);
    }

    private static BigDecimal signedCashFlowAmount(Transaction transaction) {
        BigDecimal quantity = transaction.getQuantity();
        BigDecimal price = transaction.getPrice();
        BigDecimal fee = orZero(transaction.getFee());
        BigDecimal tax = orZero(transaction.getTax());
        return switch (transaction.getTransactionType()) {
            case BUY -> price.multiply(quantity).add(fee).add(tax).negate();
            case SELL -> price.multiply(quantity).subtract(fee).subtract(tax);
            case DIVIDEND -> price.multiply(quantity);
            case SPLIT, ACQUISITION, MERGER -> null;
        };
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal asPercent(BigDecimal fraction) {
        return fraction == null ? null : fraction.multiply(HUNDRED).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}
