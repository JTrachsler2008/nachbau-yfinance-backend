package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.PortfolioRiskService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * YOUNGOITV-434: Risikokennzahlen eines Portfolios. Die Eigentumsprüfung erledigt
 * {@code PortfolioRiskService} über {@code PortfolioService.getOwnedOrThrow}, deshalb steht sie hier
 * nicht doppelt.
 *
 * <p>Der Zeitraum ist in Kalendertagen angegeben und nicht in Handelstagen, weil er einen
 * Kursabruf-Bereich beschreibt. Wie viele Handelstage darin liegen, sagt {@code observations} in der
 * Antwort.</p>
 */
@RestController
@RequestMapping("/portfolios/{portfolioId}")
public class RiskController {

    private static final int MIN_LOOKBACK_DAYS = 30;
    private static final int MAX_LOOKBACK_DAYS = 3650;
    private static final String DEFAULT_BENCHMARK = "SPY";

    private final PortfolioRiskService portfolioRiskService;

    public RiskController(PortfolioRiskService portfolioRiskService) {
        this.portfolioRiskService = portfolioRiskService;
    }

    @GetMapping("/risk")
    public RiskAnalysisResponseDto risk(
            Principal principal,
            @PathVariable Long portfolioId,
            @RequestParam(defaultValue = "365") int lookbackDays,
            @RequestParam(defaultValue = DEFAULT_BENCHMARK) String benchmark) {
        if (lookbackDays < MIN_LOOKBACK_DAYS || lookbackDays > MAX_LOOKBACK_DAYS) {
            throw new InvalidSimulationParameterException(
                    "lookbackDays must be between " + MIN_LOOKBACK_DAYS + " and " + MAX_LOOKBACK_DAYS);
        }
        if (benchmark.isBlank()) {
            throw new InvalidSimulationParameterException("benchmark must not be blank");
        }
        return portfolioRiskService.analyse(portfolioId, principal.getName(), lookbackDays, benchmark.trim().toUpperCase());
    }
}
