package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.RiskAnalysisResponseDto;
import ch.allianz.youngoitv.jt.exception.InvalidSimulationParameterException;
import ch.allianz.youngoitv.jt.service.PortfolioRiskService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * haben sie Vorrang - dieser Controller ist die einzige Stelle, die diese Wahl auflöst, der Dienst
 * darunter kennt nur noch das fertige Intervall.</p>
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = DEFAULT_BENCHMARK) String benchmark) {
        if (benchmark.isBlank()) {
            throw new InvalidSimulationParameterException("benchmark must not be blank");
        }
        LocalDate[] range = resolveRange(lookbackDays, from, to);
        return portfolioRiskService.analyse(
                portfolioId, principal.getName(), range[0], range[1], benchmark.trim().toUpperCase());
    }

    /**
     * Liefert {@code [from, to]}. Ein freies Intervall braucht beide Enden - nur eines von beiden
     * anzugeben, ist keine gültige Wahl zwischen den beiden Wegen, sondern ein unvollständiger
     * Aufruf.
     */
    private LocalDate[] resolveRange(int lookbackDays, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            if (lookbackDays < MIN_LOOKBACK_DAYS || lookbackDays > MAX_LOOKBACK_DAYS) {
                throw new InvalidSimulationParameterException(
                        "lookbackDays must be between " + MIN_LOOKBACK_DAYS + " and " + MAX_LOOKBACK_DAYS);
            }
            LocalDate resolvedTo = LocalDate.now().minusDays(1);
            return new LocalDate[] {resolvedTo.minusDays(lookbackDays), resolvedTo};
        }
        if (from == null || to == null) {
            throw new InvalidSimulationParameterException("from and to must both be given for a custom range");
        }
        if (to.isAfter(LocalDate.now().minusDays(1))) {
            throw new InvalidSimulationParameterException("to must not be after yesterday");
        }
        if (!from.isBefore(to)) {
            throw new InvalidSimulationParameterException("from must be before to");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < MIN_LOOKBACK_DAYS || days > MAX_LOOKBACK_DAYS) {
            throw new InvalidSimulationParameterException(
                    "the range between from and to must be between " + MIN_LOOKBACK_DAYS + " and "
                            + MAX_LOOKBACK_DAYS + " days");
        }
        return new LocalDate[] {from, to};
    }
}
