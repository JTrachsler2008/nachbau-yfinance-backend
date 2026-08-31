package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.PortfolioRiskService;
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
 * YOUNGOITV-434: Risikokennzahlen eines Portfolios. Die Eigentumsprüfung erledigt
 * {@code PortfolioRiskService} über {@code PortfolioService.getOwnedOrThrow}, deshalb steht sie hier
 * nicht doppelt.
 *
 * <p>Zwei Wege zum Zeitraum: entweder {@code lookbackDays} (Kalendertage vor gestern, für die
 * Presets der Oberfläche) oder ein freies {@code from}/{@code to}. Werden beide Daten mitgeschickt,
 * haben sie Vorrang. Diese Wahl löst {@link DateRange} auf, weil der Wertverlauf
 * ({@code PerformanceController}) sie inzwischen mit denselben Grenzen genauso trifft; der Dienst
 * darunter kennt nur noch das fertige Intervall.</p>
 */
@RestController
@RequestMapping("/portfolios/{portfolioId}")
public class RiskController {

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = DEFAULT_BENCHMARK) String benchmark) {
        if (benchmark.isBlank()) {
            throw new InvalidSimulationParameterException("benchmark must not be blank");
        }
        DateRange range = DateRange.resolve(lookbackDays, from, to);
        return portfolioRiskService.analyse(
                portfolioId, principal.getName(), range.from(), range.to(), benchmark.trim().toUpperCase());
    }
}
