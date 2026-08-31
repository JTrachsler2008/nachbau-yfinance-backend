package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.CurrencyAmountResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioHistoryResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioReturnsResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioValuationResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.DividendsService;
import ch.allianz.youngoitv.jt.service.InterestService;
import ch.allianz.youngoitv.jt.service.PortfolioHistoryService;
import ch.allianz.youngoitv.jt.service.PortfolioReturnsService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PortfolioValuationService;
import ch.allianz.youngoitv.jt.service.RealizedGainsService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import ch.allianz.youngoitv.jt.util.DateRange;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * YOUNGOITV-433: realisierte Gewinne, Dividendenerträge und Zinserträge über die gesamte
 * Transaktionshistorie eines Portfolios, konsistent FX-konvertiert in die angeforderte
 * Anzeigewährung.
 *
 * <p>Marktwert und geldgewichtete Rendite ({@code /valuation}, {@code /returns}) brauchen nur einen
 * Livekurs je Position und eine Währungsumrechnung. Die zeitgewichtete Rendite braucht mehr, nämlich
 * eine vollständige Neubewertung an jedem Stichtag der Vergangenheit - deshalb liegt sie nicht bei
 * {@code /returns}, sondern bei {@code /history}, zusammen mit dem Wertverlauf, der aus derselben
 * Rechnung fällt. {@code timeWeightedReturn} in {@code PortfolioReturnsResponseDto} bleibt aus
 * Rücksicht auf bestehende Aufrufe {@code null} und ist dort als überholt vermerkt.</p>
 *
 * <p>{@code /history} löst seinen Zeitraum über {@link DateRange} auf, mit denselben Grenzen und
 * derselben Vorrangregel wie {@code /risk}: die Oberfläche bietet auf Performance- und Risikoseite
 * dieselben Presets an, und zwei Endpunkte, die "letztes Jahr" verschieden auslegen, würden zwei
 * Zahlen zeigen, die niemand zusammenbringt.</p>
 */
@RestController
@RequestMapping("/portfolios/{portfolioId}")
public class PerformanceController {

    private static final String DEFAULT_BENCHMARK = "SPY";

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final RealizedGainsService realizedGainsService;
    private final DividendsService dividendsService;
    private final InterestService interestService;
    private final PortfolioValuationService portfolioValuationService;
    private final PortfolioReturnsService portfolioReturnsService;
    private final PortfolioHistoryService portfolioHistoryService;

    public PerformanceController(
            PortfolioService portfolioService,
            TransactionService transactionService,
            RealizedGainsService realizedGainsService,
            DividendsService dividendsService,
            InterestService interestService,
            PortfolioValuationService portfolioValuationService,
            PortfolioReturnsService portfolioReturnsService,
            PortfolioHistoryService portfolioHistoryService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.realizedGainsService = realizedGainsService;
        this.dividendsService = dividendsService;
        this.interestService = interestService;
        this.portfolioValuationService = portfolioValuationService;
        this.portfolioReturnsService = portfolioReturnsService;
        this.portfolioHistoryService = portfolioHistoryService;
    }

    @GetMapping("/realized-gains")
    public CurrencyAmountResponseDto realizedGains(
            Principal principal, @PathVariable Long portfolioId, @RequestParam String currency) {
        portfolioService.getOwnedOrThrow(portfolioId, principal.getName());
        var transactions = transactionService.getTransactionsForPortfolio(portfolioId);
        return new CurrencyAmountResponseDto(
                realizedGainsService.calculateTotalInCurrency(transactions, currency), currency);
    }

    @GetMapping("/dividends")
    public CurrencyAmountResponseDto dividends(
            Principal principal, @PathVariable Long portfolioId, @RequestParam String currency) {
        portfolioService.getOwnedOrThrow(portfolioId, principal.getName());
        var transactions = transactionService.getTransactionsForPortfolio(portfolioId);
        return new CurrencyAmountResponseDto(
                dividendsService.calculateTotalInCurrency(transactions, currency), currency);
    }

    /**
     * Zinsertrag aus allen Coupon-Buchungen. Eigener Endpunkt und nicht Teil von {@code /dividends},
     * weil Zins und Dividende zwei getrennt zu lesende Erträge sind (siehe {@link InterestService}).
     */
    @GetMapping("/interest")
    public CurrencyAmountResponseDto interest(
            Principal principal, @PathVariable Long portfolioId, @RequestParam String currency) {
        portfolioService.getOwnedOrThrow(portfolioId, principal.getName());
        var transactions = transactionService.getTransactionsForPortfolio(portfolioId);
        return new CurrencyAmountResponseDto(
                interestService.calculateTotalInCurrency(transactions, currency), currency);
    }

    @GetMapping("/valuation")
    public PortfolioValuationResponseDto valuation(Principal principal, @PathVariable Long portfolioId) {
        return portfolioValuationService.currentValuation(portfolioId, principal.getName());
    }

    @GetMapping("/returns")
    public PortfolioReturnsResponseDto returns(Principal principal, @PathVariable Long portfolioId) {
        return portfolioReturnsService.returns(portfolioId, principal.getName());
    }

    @GetMapping("/history")
    public PortfolioHistoryResponseDto history(
            Principal principal,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "365") int lookbackDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = DEFAULT_BENCHMARK) String benchmark) {
        if (benchmark.isBlank()) {
            throw new InvalidSimulationParameterException("benchmark must not be blank");
        }
        DateRange range = DateRange.resolve(lookbackDays, from, to);
        return portfolioHistoryService.history(
                portfolioId, principal.getName(), range.from(), range.to(), benchmark.trim().toUpperCase());
    }
}
