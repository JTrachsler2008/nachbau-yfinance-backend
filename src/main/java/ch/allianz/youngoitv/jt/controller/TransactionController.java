package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.LotResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioTransactionResponseDto;
import ch.allianz.youngoitv.jt.dto.TransactionRequestDto;
import ch.allianz.youngoitv.jt.dto.TransactionResponseDto;
import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.mapper.LotMapper;
import ch.allianz.youngoitv.jt.mapper.TransactionMapper;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService transactionService;
    private final PortfolioService portfolioService;
    private final TransactionMapper transactionMapper;
    private final LotMapper lotMapper;

    public TransactionController(
            TransactionService transactionService,
            PortfolioService portfolioService,
            TransactionMapper transactionMapper,
            LotMapper lotMapper) {
        this.transactionService = transactionService;
        this.portfolioService = portfolioService;
        this.transactionMapper = transactionMapper;
        this.lotMapper = lotMapper;
    }

    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<TransactionResponseDto> create(
            Principal principal, @PathVariable Long accountId, @Valid @RequestBody TransactionRequestDto request) {
        var transaction = transactionService.createTransaction(accountId, principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionMapper.toResponseDto(transaction));
    }

    /**
     * Transaktionshistorie eines ganzen Portfolios, absteigend nach Datum.
     *
     * <p>Die Prüfung über {@code getOwnedOrThrow} steht hier und nicht im Service: {@code
     * getTransactionsForPortfolio} nimmt keinen Benutzernamen und prüft daher nichts. Ohne diese Zeile
     * könnte jeder angemeldete Benutzer die Historie eines fremden Portfolios lesen.</p>
     */
    @GetMapping("/portfolios/{portfolioId}/transactions")
    public List<PortfolioTransactionResponseDto> forPortfolio(Principal principal, @PathVariable Long portfolioId) {
        portfolioService.getOwnedOrThrow(portfolioId, principal.getName());
        // Die Id als zweites Kriterium hält die Reihenfolge bei mehreren Buchungen am selben Tag stabil.
        // Das Repository liefert aufsteigend, die Liste im Frontend zeigt das Neueste oben.
        Comparator<Transaction> neuesteZuerst = Comparator.<Transaction, LocalDate>comparing(
                        Transaction::getTransactionDate)
                .thenComparing(Transaction::getId)
                .reversed();
        return transactionService.getTransactionsForPortfolio(portfolioId).stream()
                .sorted(neuesteZuerst)
                .map(transactionMapper::toPortfolioResponseDto)
                .toList();
    }

    @GetMapping("/accounts/{accountId}/positions/{securityId}/lots")
    public List<LotResponseDto> lots(
            Principal principal, @PathVariable Long accountId, @PathVariable Long securityId) {
        return transactionService.getOpenLots(accountId, securityId, principal.getName()).stream()
                .map(lotMapper::toResponseDto)
                .toList();
    }
}
