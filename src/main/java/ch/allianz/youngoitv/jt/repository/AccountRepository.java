package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByPortfolioId(Long portfolioId);
}
