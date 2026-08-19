package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.TransactionRequestDto;
import ch.allianz.youngoitv.jt.entity.Transaction;
import java.util.List;

public interface TransactionService {

    Transaction createTransaction(Long accountId, String username, TransactionRequestDto request);

    List<Lot> getOpenLots(Long accountId, Long securityId, String username);

    List<Transaction> getTransactionsForPortfolio(Long portfolioId);
}
