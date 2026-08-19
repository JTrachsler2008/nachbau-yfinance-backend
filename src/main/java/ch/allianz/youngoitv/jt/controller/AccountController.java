package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.AccountCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.AccountResponseDto;
import ch.allianz.youngoitv.jt.dto.CashMovementRequestDto;
import ch.allianz.youngoitv.jt.mapper.AccountMapper;
import ch.allianz.youngoitv.jt.service.AccountService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    public AccountController(AccountService accountService, AccountMapper accountMapper) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
    }

    @PostMapping("/portfolios/{portfolioId}/accounts")
    public ResponseEntity<AccountResponseDto> create(
            Principal principal, @PathVariable Long portfolioId, @Valid @RequestBody AccountCreateRequestDto request) {
        var account = accountService.create(portfolioId, principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountMapper.toResponseDto(account));
    }

    @GetMapping("/portfolios/{portfolioId}/accounts")
    public List<AccountResponseDto> list(Principal principal, @PathVariable Long portfolioId) {
        return accountService.listForPortfolio(portfolioId, principal.getName()).stream()
                .map(accountMapper::toResponseDto)
                .toList();
    }

    @PostMapping("/accounts/{id}/deposit")
    public AccountResponseDto deposit(
            Principal principal, @PathVariable Long id, @Valid @RequestBody CashMovementRequestDto request) {
        return accountMapper.toResponseDto(accountService.deposit(id, principal.getName(), request.amount()));
    }

    @PostMapping("/accounts/{id}/withdraw")
    public AccountResponseDto withdraw(
            Principal principal, @PathVariable Long id, @Valid @RequestBody CashMovementRequestDto request) {
        return accountMapper.toResponseDto(accountService.withdraw(id, principal.getName(), request.amount()));
    }
}
