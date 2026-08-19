package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByTransactionDateAsc(Long accountId);

    List<Transaction> findByAccountIdAndSecurityIdOrderByTransactionDateAsc(Long accountId, Long securityId);

    List<Transaction> findByAccountPortfolioIdOrderByTransactionDateAsc(Long portfolioId);
}
