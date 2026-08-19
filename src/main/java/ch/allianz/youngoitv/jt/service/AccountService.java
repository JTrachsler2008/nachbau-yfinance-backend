package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.AccountCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Account;
import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    Account create(Long portfolioId, String username, AccountCreateRequestDto request);

    List<Account> listForPortfolio(Long portfolioId, String username);

    Account getOwnedOrThrow(Long accountId, String username);

    Account deposit(Long accountId, String username, BigDecimal amount);

    Account withdraw(Long accountId, String username, BigDecimal amount);
}
