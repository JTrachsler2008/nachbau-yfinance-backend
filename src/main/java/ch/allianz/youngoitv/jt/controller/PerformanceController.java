package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.CurrencyAmountResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioReturnsResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioValuationResponseDto;
import ch.allianz.youngoitv.jt.service.DividendsService;
import ch.allianz.youngoitv.jt.service.PortfolioReturnsService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.PortfolioValuationService;
import ch.allianz.youngoitv.jt.service.RealizedGainsService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * YOUNGOITV-433: realisierte Gewinne und Dividendenerträge über die gesamte Transaktionshistorie
 * eines Portfolios, konsistent FX-konvertiert in die angeforderte Anzeigewährung.
 *
 * <p>Marktwert und geldgewichtete Rendite ({@code /valuation}, {@code /returns}) schliessen die
 * ursprünglich als Folgearbeit vermerkte Lücke aus YOUNGOITV-432 teilweise: beide brauchen einen
 * Livekurs je Position und eine Währungsumrechnung, nicht aber die vollständige historische
 * Neubewertung, die die zeitgewichtete Rendite bräuchte. {@code timeWeightedReturn} bleibt deshalb
 * weiterhin {@code null} (siehe {@code PortfolioReturnsResponseDto}), Risikokennzahlen
 * (YOUNGOITV-434) haben mit {@code PortfolioRiskServiceImpl} inzwischen einen eigenen Endpunkt.</p>
 */
@RestController
@RequestMapping("/portfolios/{portfolioId}")
public class PerformanceController {

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final RealizedGainsService realizedGainsService;
    private final DividendsService dividendsService;
    private final PortfolioValuationService portfolioValuationService;
    private final PortfolioReturnsService portfolioReturnsService;

    public PerformanceController(
            PortfolioService portfolioService,
            TransactionService transactionService,
            RealizedGainsService realizedGainsService,
            DividendsService dividendsService,
            PortfolioValuationService portfolioValuationService,
            PortfolioReturnsService portfolioReturnsService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.realizedGainsService = realizedGainsService;
        this.dividendsService = dividendsService;
        this.portfolioValuationService = portfolioValuationService;
        this.portfolioReturnsService = portfolioReturnsService;
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

    @GetMapping("/valuation")
    public PortfolioValuationResponseDto valuation(Principal principal, @PathVariable Long portfolioId) {
        return portfolioValuationService.currentValuation(portfolioId, principal.getName());
    }

    @GetMapping("/returns")
    public PortfolioReturnsResponseDto returns(Principal principal, @PathVariable Long portfolioId) {
        return portfolioReturnsService.returns(portfolioId, principal.getName());
    }
}
