package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoLotServiceRealizedGainsTest {

    private final FifoLotService fifoLotService = new FifoLotService();

    private Transaction transaction(TransactionType type, BigDecimal quantity, BigDecimal price, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setTransactionType(type);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setTransactionCurrency("CHF");
        tx.setTransactionDate(date);
        return tx;
    }

    /**
     * Handrechnung: Kauf 10 Stück zu 100, Verkauf 10 Stück zu 150.
     * Erlös = 10*150 = 1500. Kostenbasis (FIFO) = 10*100 = 1000. Gewinn = 500.
     */
    @Test
    void sellingAtAHigherPriceThanCostBasisProducesAPositiveGain() {
        List<Transaction> history = List.of(
                transaction(TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                transaction(TransactionType.SELL, new BigDecimal("10"), new BigDecimal("150"), LocalDate.of(2026, 2, 1)));

        List<RealizedGain> gains = fifoLotService.calculateRealizedGains(history);

        assertThat(gains).hasSize(1);
        assertThat(gains.get(0).amount()).isEqualByComparingTo("500");
    }

    /**
     * Handrechnung: Kauf 10@100 dann 10@150 (FIFO: älteste Tranche zuerst). Verkauf von 15 Stück
     * zu 120: 10 Stück aus der ersten Tranche (Kostenbasis 10*100=1000) + 5 Stück aus der zweiten
     * (Kostenbasis 5*150=750) = Kostenbasis 1750. Erlös = 15*120 = 1800. Gewinn = 50.
     */
    @Test
    void sellAcrossTwoLotsUsesWeightedFifoCostBasis() {
        List<Transaction> history = List.of(
                transaction(TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                transaction(TransactionType.BUY, new BigDecimal("10"), new BigDecimal("150"), LocalDate.of(2026, 2, 1)),
                transaction(TransactionType.SELL, new BigDecimal("15"), new BigDecimal("120"), LocalDate.of(2026, 3, 1)));

        List<RealizedGain> gains = fifoLotService.calculateRealizedGains(history);

        assertThat(gains).hasSize(1);
        assertThat(gains.get(0).amount()).isEqualByComparingTo("50");
    }

    /**
     * Handrechnung: Kauf 10@100, Verkauf 10@150 mit Gebühr 5 und Steuer 3.
     * Erlös = 10*150 - 5 - 3 = 1492. Kostenbasis = 1000. Gewinn = 492 (nicht 500).
     */
    @Test
    void feeAndTaxOnSellReduceTheRealizedGain() {
        Transaction sell = transaction(TransactionType.SELL, new BigDecimal("10"), new BigDecimal("150"),
                LocalDate.of(2026, 2, 1));
        sell.setFee(new BigDecimal("5"));
        sell.setTax(new BigDecimal("3"));
        List<Transaction> history = List.of(
                transaction(TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                sell);

        List<RealizedGain> gains = fifoLotService.calculateRealizedGains(history);

        assertThat(gains).hasSize(1);
        assertThat(gains.get(0).amount()).isEqualByComparingTo("492");
    }

    @Test
    void dividendsAndSplitsDoNotProduceRealizedGainEntries() {
        List<Transaction> history = List.of(
                transaction(TransactionType.BUY, new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                transaction(TransactionType.DIVIDEND, new BigDecimal("10"), new BigDecimal("2"), LocalDate.of(2026, 1, 15)));

        List<RealizedGain> gains = fifoLotService.calculateRealizedGains(history);

        assertThat(gains).isEmpty();
    }
}
