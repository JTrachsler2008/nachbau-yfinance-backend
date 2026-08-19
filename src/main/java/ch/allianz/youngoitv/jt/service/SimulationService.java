package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.BacktestResponseDto;
import ch.allianz.youngoitv.jt.dto.PurchaseSimulationResponseDto;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface SimulationService {

    PurchaseSimulationResponseDto simulatePurchase(Long portfolioId, String username, String symbol, BigDecimal quantity);

    BacktestResponseDto backtest(String symbol, BigDecimal quantity, LocalDate purchaseDate);
}
