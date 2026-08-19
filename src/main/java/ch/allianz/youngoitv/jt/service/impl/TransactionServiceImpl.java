package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.dto.TransactionRequestDto;
import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import ch.allianz.youngoitv.jt.exception.InsufficientFundsException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.repository.PositionRepository;
import ch.allianz.youngoitv.jt.repository.TransactionRepository;
import ch.allianz.youngoitv.jt.service.AccountService;
import ch.allianz.youngoitv.jt.service.FifoLotService;
import ch.allianz.youngoitv.jt.service.FxRateService;
import ch.allianz.youngoitv.jt.service.Lot;
import ch.allianz.youngoitv.jt.service.SecurityService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final int SCALE = 4;

    private final AccountService accountService;
    private final SecurityService securityService;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final FifoLotService fifoLotService;
    private final MarketDataProvider marketDataProvider;
    private final FxRateService fxRateService;

    public TransactionServiceImpl(
            AccountService accountService,
            SecurityService securityService,
            PositionRepository positionRepository,
            TransactionRepository transactionRepository,
            FifoLotService fifoLotService,
            MarketDataProvider marketDataProvider,
            FxRateService fxRateService) {
        this.accountService = accountService;
        this.securityService = securityService;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.fifoLotService = fifoLotService;
        this.marketDataProvider = marketDataProvider;
        this.fxRateService = fxRateService;
    }

    @Override
    @Transactional
    public Transaction createTransaction(Long accountId, String username, TransactionRequestDto request) {
        Account account = accountService.getOwnedOrThrow(accountId, username);
        Security security = securityService.getByIdOrThrow(request.securityId());

        BigDecimal price = request.transactionType() == TransactionType.SPLIT
                ? null
                : resolvePrice(request, security);
        BigDecimal fee = request.fee() == null ? BigDecimal.ZERO : request.fee();
        BigDecimal tax = request.tax() == null ? BigDecimal.ZERO : request.tax();

        Position position = positionRepository.findByAccountIdAndSecurityId(accountId, security.getId())
                .orElseGet(() -> newEmptyPosition(account, security));

        switch (request.transactionType()) {
            case BUY -> applyBuy(account, position, request, price, fee, tax);
            case SELL -> applySell(account, position, request, price, fee, tax);
            case DIVIDEND -> applyDividend(account, request, price);
            case SPLIT -> applySplit(position, request);
            case ACQUISITION, MERGER -> applyAcquisitionOrMerger(position, request, price);
        }

        positionRepository.save(position);

        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setSecurity(security);
        transaction.setTransactionType(request.transactionType());
        transaction.setQuantity(request.quantity());
        transaction.setPrice(price);
        transaction.setFee(fee);
        transaction.setTax(tax);
        transaction.setSplitRatio(request.splitRatio());
        transaction.setTransactionCurrency(request.transactionCurrency());
        transaction.setFxRateToPortfolio(resolveFxRate(account, request));
        transaction.setTransactionDate(request.transactionDate());
        return transactionRepository.save(transaction);
    }

    @Override
    public List<Lot> getOpenLots(Long accountId, Long securityId, String username) {
        accountService.getOwnedOrThrow(accountId, username);
        List<Transaction> history = transactionRepository
                .findByAccountIdAndSecurityIdOrderByTransactionDateAsc(accountId, securityId);
        return fifoLotService.calculateOpenLots(history);
    }

    private void applyBuy(Account account, Position position, TransactionRequestDto request,
            BigDecimal price, BigDecimal fee, BigDecimal tax) {
        BigDecimal totalCost = price.multiply(request.quantity()).add(fee).add(tax);
        if (account.getCashAmount().compareTo(totalCost) < 0) {
            throw new InsufficientFundsException(
                    "Account " + account.getId() + " has insufficient cash for a BUY of " + totalCost);
        }

        BigDecimal existingCost = position.getTotalQuantity().multiply(position.getAveragePurchasePrice());
        BigDecimal newQuantity = position.getTotalQuantity().add(request.quantity());
        BigDecimal newCost = existingCost.add(price.multiply(request.quantity())).add(fee).add(tax);

        position.setTotalQuantity(newQuantity);
        position.setAveragePurchasePrice(newCost.divide(newQuantity, SCALE, RoundingMode.HALF_UP));
        account.setCashAmount(account.getCashAmount().subtract(totalCost));
    }

    private void applySell(Account account, Position position, TransactionRequestDto request,
            BigDecimal price, BigDecimal fee, BigDecimal tax) {
        if (position.getTotalQuantity().compareTo(request.quantity()) < 0) {
            throw new InsufficientFundsException(
                    "Account " + account.getId() + " holds fewer shares than requested for this SELL");
        }

        position.setTotalQuantity(position.getTotalQuantity().subtract(request.quantity()));
        BigDecimal proceeds = price.multiply(request.quantity()).subtract(fee).subtract(tax);
        account.setCashAmount(account.getCashAmount().add(proceeds));
    }

    private void applyDividend(Account account, TransactionRequestDto request, BigDecimal price) {
        account.setCashAmount(account.getCashAmount().add(price.multiply(request.quantity())));
    }

    private void applySplit(Position position, TransactionRequestDto request) {
        BigDecimal ratio = request.splitRatio();
        position.setTotalQuantity(position.getTotalQuantity().multiply(ratio));
        position.setAveragePurchasePrice(position.getAveragePurchasePrice().divide(ratio, SCALE, RoundingMode.HALF_UP));
    }

    private void applyAcquisitionOrMerger(Position position, TransactionRequestDto request, BigDecimal price) {
        position.setTotalQuantity(request.quantity());
        position.setAveragePurchasePrice(price);
    }

    private BigDecimal resolvePrice(TransactionRequestDto request, Security security) {
        if (request.price() != null) {
            return request.price();
        }
        return marketDataProvider.getQuote(security.getSymbol())
                .map(quote -> quote.price())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No price given and no live quote available for " + security.getSymbol()));
    }

    private BigDecimal resolveFxRate(Account account, TransactionRequestDto request) {
        String portfolioCurrency = account.getPortfolio().getBaseCurrency();
        if (request.transactionCurrency().equals(portfolioCurrency)) {
            return BigDecimal.ONE;
        }
        return fxRateService.getLatestOnOrBeforeOrThrow(
                        request.transactionCurrency(), portfolioCurrency, request.transactionDate())
                .getRate();
    }

    private Position newEmptyPosition(Account account, Security security) {
        Position position = new Position();
        position.setAccount(account);
        position.setSecurity(security);
        position.setTotalQuantity(BigDecimal.ZERO);
        position.setAveragePurchasePrice(BigDecimal.ZERO);
        return position;
    }
}
