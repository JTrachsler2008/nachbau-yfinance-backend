package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.CurrencyAmountResponseDto;
import ch.allianz.youngoitv.jt.service.DividendsService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.RealizedGainsService;
import ch.allianz.youngoitv.jt.service.TransactionService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * YOUNGOITV-433: realisierte Gewinne und Dividendenertraege ueber die gesamte Transaktionshistorie
 * eines Portfolios, konsistent FX-konvertiert in die angeforderte Anzeigewaehrung.
 *
 * <p>Bewusst noch nicht Teil dieses Tickets: Gesamtwert/TWR/MWR (YOUNGOITV-432) und Risikokennzahlen
 * (YOUNGOITV-434) benoetigen eine vollstaendige historische Neubewertung aller Positionen ueber
 * Live-Kursdaten - die Berechnungslogik dafuer ({@code TwrService}, {@code MwrService},
 * {@code RiskService}) ist bereits implementiert und unabhaengig getestet, die Zusammenstellung der
 * dafuer noetigen Wertreihen aus echten Live-Kursen ist als Folgearbeit vorgesehen, um hier keine
 * unvollstaendige/unzuverlaessige Endpunkt-Antwort auszuliefern.</p>
 */
@RestController
@RequestMapping("/portfolios/{portfolioId}")
public class PerformanceController {

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final RealizedGainsService realizedGainsService;
    private final DividendsService dividendsService;

    public PerformanceController(
            PortfolioService portfolioService,
            TransactionService transactionService,
            RealizedGainsService realizedGainsService,
            DividendsService dividendsService) {
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
        this.realizedGainsService = realizedGainsService;
        this.dividendsService = dividendsService;
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
}
