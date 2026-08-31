package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.allianz.youngoitv.jt.entity.Transaction;
import ch.allianz.youngoitv.jt.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoLotServiceTest {

    private final FifoLotService fifoLotService = new FifoLotService();

    private Transaction buy(BigDecimal quantity, BigDecimal price, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setTransactionType(TransactionType.BUY);
        tx.setQuantity(quantity);
        tx.setPrice(price);
        tx.setTransactionDate(date);
        return tx;
    }

    private Transaction sell(BigDecimal quantity, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setTransactionType(TransactionType.SELL);
        tx.setQuantity(quantity);
        tx.setTransactionDate(date);
        return tx;
    }

    private Transaction split(BigDecimal ratio, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setTransactionType(TransactionType.SPLIT);
        tx.setSplitRatio(ratio);
        tx.setTransactionDate(date);
        return tx;
    }

    @Test
    void sellConsumesOldestLotFirst() {
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                buy(new BigDecimal("10"), new BigDecimal("150"), LocalDate.of(2026, 2, 1)),
                sell(new BigDecimal("10"), LocalDate.of(2026, 3, 1)));

        List<Lot> lots = fifoLotService.calculateOpenLots(history);

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("150");
    }

    @Test
    void sellPartiallyConsumingOldestLotLeavesTheRemainder() {
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                buy(new BigDecimal("10"), new BigDecimal("150"), LocalDate.of(2026, 2, 1)),
                sell(new BigDecimal("4"), LocalDate.of(2026, 3, 1)));

        List<Lot> lots = fifoLotService.calculateOpenLots(history);

        assertThat(lots).hasSize(2);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("6");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("100");
        assertThat(lots.get(1).quantity()).isEqualByComparingTo("10");
    }

    @Test
    void sellSpanningTwoLotsConsumesTheFirstFullyAndTheSecondPartially() {
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                buy(new BigDecimal("10"), new BigDecimal("150"), LocalDate.of(2026, 2, 1)),
                sell(new BigDecimal("15"), LocalDate.of(2026, 3, 1)));

        List<Lot> lots = fifoLotService.calculateOpenLots(history);

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("5");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("150");
    }

    @Test
    void splitScalesAllOpenLotsByTheRatio() {
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                split(new BigDecimal("2"), LocalDate.of(2026, 2, 1)));

        List<Lot> lots = fifoLotService.calculateOpenLots(history);

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("20");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("50.0000");
    }

    /**
     * Fälligkeit einer Anleihe: die ganze Menge wird zurückgezahlt, danach ist keine Tranche mehr
     * offen. Ohne diesen Abbau bliebe die Anleihe nach der Rückzahlung im Bestand stehen und würde in
     * jeder Bewertung weiter mitgezählt.
     */
    @Test
    void redemptionConsumesTheOpenLotsLikeASell() {
        Transaction redemption = new Transaction();
        redemption.setTransactionType(TransactionType.REDEMPTION);
        redemption.setQuantity(new BigDecimal("10"));
        redemption.setPrice(new BigDecimal("100"));
        redemption.setTransactionDate(LocalDate.of(2026, 3, 1));
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("95"), LocalDate.of(2026, 1, 1)),
                redemption);

        assertThat(fifoLotService.calculateOpenLots(history)).isEmpty();
    }

    /** Ein Coupon zahlt Zins aus und rührt den Bestand nicht an. */
    @Test
    void couponLeavesTheOpenLotsUntouched() {
        Transaction coupon = new Transaction();
        coupon.setTransactionType(TransactionType.COUPON);
        coupon.setQuantity(new BigDecimal("10"));
        coupon.setPrice(new BigDecimal("2.5"));
        coupon.setTransactionDate(LocalDate.of(2026, 2, 1));
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("95"), LocalDate.of(2026, 1, 1)),
                coupon);

        List<Lot> lots = fifoLotService.calculateOpenLots(history);

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("95");
    }

    @Test
    void mergerReplacesAllOpenLotsWithTheTransactionValues() {
        List<Transaction> history = List.of(
                buy(new BigDecimal("10"), new BigDecimal("100"), LocalDate.of(2026, 1, 1)),
                buy(new BigDecimal("5"), new BigDecimal("120"), LocalDate.of(2026, 2, 1)));
        Transaction merger = new Transaction();
        merger.setTransactionType(TransactionType.MERGER);
        merger.setQuantity(new BigDecimal("20"));
        merger.setPrice(new BigDecimal("80"));
        merger.setTransactionDate(LocalDate.of(2026, 3, 1));

        List<Lot> lots = fifoLotService.calculateOpenLots(
                java.util.stream.Stream.concat(history.stream(), java.util.stream.Stream.of(merger)).toList());

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).quantity()).isEqualByComparingTo("20");
        assertThat(lots.get(0).purchasePrice()).isEqualByComparingTo("80");
    }
}
