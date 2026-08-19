package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.BacktestResponseDto;
import ch.allianz.youngoitv.jt.dto.PurchaseSimulationResponseDto;
import ch.allianz.youngoitv.jt.service.SimulationService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/purchase")
    public PurchaseSimulationResponseDto simulatePurchase(
            Principal principal,
            @RequestParam Long portfolioId,
            @RequestParam String symbol,
            @RequestParam BigDecimal quantity) {
        return simulationService.simulatePurchase(portfolioId, principal.getName(), symbol, quantity);
    }

    @GetMapping("/backtest")
    public BacktestResponseDto backtest(
            @RequestParam String symbol,
            @RequestParam BigDecimal quantity,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate) {
        return simulationService.backtest(symbol, quantity, purchaseDate);
    }
}
