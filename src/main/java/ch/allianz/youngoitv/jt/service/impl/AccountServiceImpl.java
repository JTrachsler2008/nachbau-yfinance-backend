package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.AccountCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.exception.InsufficientFundsException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException;
import ch.allianz.youngoitv.jt.repository.AccountRepository;
import ch.allianz.youngoitv.jt.security.OwnerCheckService;
import ch.allianz.youngoitv.jt.service.AccountService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final PortfolioService portfolioService;
    private final UserService userService;
    private final OwnerCheckService ownerCheckService;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            PortfolioService portfolioService,
            UserService userService,
            OwnerCheckService ownerCheckService) {
        this.accountRepository = accountRepository;
        this.portfolioService = portfolioService;
        this.userService = userService;
        this.ownerCheckService = ownerCheckService;
    }

    @Override
    public Account create(Long portfolioId, String username, AccountCreateRequestDto request) {
        Portfolio portfolio = portfolioService.getOwnedOrThrow(portfolioId, username);

        Account account = new Account();
        account.setPortfolio(portfolio);
        account.setName(request.name());
        account.setCurrency(request.currency());
        account.setCashAmount(BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    @Override
    public List<Account> listForPortfolio(Long portfolioId, String username) {
        portfolioService.getOwnedOrThrow(portfolioId, username);
        return accountRepository.findByPortfolioId(portfolioId);
    }

    @Override
    public Account getOwnedOrThrow(Long accountId, String username) {
        User principal = userService.getByUsernameOrThrow(username);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountId + " not found"));

        if (!ownerCheckService.isAuthorizedForAccount(account, principal)) {
            throw new UnauthorizedAccessException("Account " + accountId + " does not belong to this user");
        }
        return account;
    }

    @Override
    @Transactional
    public Account deposit(Long accountId, String username, BigDecimal amount) {
        Account account = getOwnedOrThrow(accountId, username);
        account.setCashAmount(account.getCashAmount().add(amount));
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account withdraw(Long accountId, String username, BigDecimal amount) {
        Account account = getOwnedOrThrow(accountId, username);
        if (account.getCashAmount().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Account " + accountId + " has insufficient funds for a withdrawal of " + amount);
        }
        account.setCashAmount(account.getCashAmount().subtract(amount));
        return accountRepository.save(account);
    }
}
