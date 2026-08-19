package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Eigenstaendiges Berechnungsmodell fuer offene Kauf-Tranchen, unabhaengig vom aggregierten
 * Position-Datensatz. Verfolgt offene Chargen strikt nach Kaufdatum (First-In-First-Out); SPLIT
 * skaliert alle offenen Tranchen-Mengen mit der Ratio, ACQUISITION/MERGER ersetzt sie (im Original
 * eine bekannte Luecke, hier behoben). Reine Funktion ueber eine uebergebene, nach Datum
 * aufsteigend sortierte Transaktionsliste - ohne DB-Zugriff, damit ohne Fixtures testbar.
 */
@Service
public class FifoLotService {

    public List<Lot> calculateOpenLots(List<Transaction> transactionsOrderedByDate) {
        Deque<Lot> lots = new ArrayDeque<>();

        for (Transaction tx : transactionsOrderedByDate) {
            switch (tx.getTransactionType()) {
                case BUY -> lots.addLast(new Lot(tx.getQuantity(), tx.getPrice(), tx.getTransactionDate()));
                case SELL -> reduceFifo(lots, tx.getQuantity());
                case SPLIT -> scaleLots(lots, tx.getSplitRatio());
                case ACQUISITION, MERGER -> {
                    lots.clear();
                    lots.addLast(new Lot(tx.getQuantity(), tx.getPrice(), tx.getTransactionDate()));
                }
                case DIVIDEND -> {
                    // Keine Auswirkung auf Bestands-Tranchen.
                }
            }
        }
        return new ArrayList<>(lots);
    }

    /**
     * Wie {@link #calculateOpenLots}, verfolgt aber zusaetzlich den realisierten Gewinn/Verlust
     * (Verkaufserlos minus FIFO-Kostenbasis der abgebauten Tranchen) je SELL-Transaktion.
     */
    public List<RealizedGain> calculateRealizedGains(List<Transaction> transactionsOrderedByDate) {
        Deque<Lot> lots = new ArrayDeque<>();
        List<RealizedGain> gains = new ArrayList<>();

        for (Transaction tx : transactionsOrderedByDate) {
            switch (tx.getTransactionType()) {
                case BUY -> lots.addLast(new Lot(tx.getQuantity(), tx.getPrice(), tx.getTransactionDate()));
                case SELL -> gains.add(sellAndComputeRealizedGain(lots, tx));
                case SPLIT -> scaleLots(lots, tx.getSplitRatio());
                case ACQUISITION, MERGER -> {
                    lots.clear();
                    lots.addLast(new Lot(tx.getQuantity(), tx.getPrice(), tx.getTransactionDate()));
                }
                case DIVIDEND -> {
                    // Keine Auswirkung auf Bestands-Tranchen oder realisierte Gewinne.
                }
            }
        }
        return gains;
    }

    private RealizedGain sellAndComputeRealizedGain(Deque<Lot> lots, Transaction sellTx) {
        BigDecimal remaining = sellTx.getQuantity();
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !lots.isEmpty()) {
            Lot oldest = lots.pollFirst();
            if (oldest.quantity().compareTo(remaining) <= 0) {
                totalCostBasis = totalCostBasis.add(oldest.quantity().multiply(oldest.purchasePrice()));
                remaining = remaining.subtract(oldest.quantity());
            } else {
                totalCostBasis = totalCostBasis.add(remaining.multiply(oldest.purchasePrice()));
                lots.addFirst(new Lot(
                        oldest.quantity().subtract(remaining), oldest.purchasePrice(), oldest.purchaseDate()));
                remaining = BigDecimal.ZERO;
            }
        }

        BigDecimal proceeds = sellTx.getPrice().multiply(sellTx.getQuantity());
        BigDecimal gain = proceeds.subtract(totalCostBasis);
        return new RealizedGain(gain, sellTx.getTransactionCurrency(), sellTx.getTransactionDate());
    }

    private void reduceFifo(Deque<Lot> lots, BigDecimal quantityToSell) {
        BigDecimal remaining = quantityToSell;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && !lots.isEmpty()) {
            Lot oldest = lots.pollFirst();
            int comparison = oldest.quantity().compareTo(remaining);
            if (comparison <= 0) {
                remaining = remaining.subtract(oldest.quantity());
            } else {
                lots.addFirst(new Lot(
                        oldest.quantity().subtract(remaining), oldest.purchasePrice(), oldest.purchaseDate()));
                remaining = BigDecimal.ZERO;
            }
        }
    }

    private void scaleLots(Deque<Lot> lots, BigDecimal splitRatio) {
        List<Lot> scaled = lots.stream()
                .map(lot -> new Lot(
                        lot.quantity().multiply(splitRatio),
                        lot.purchasePrice().divide(splitRatio, 4, RoundingMode.HALF_UP),
                        lot.purchaseDate()))
                .toList();
        lots.clear();
        lots.addAll(scaled);
    }
}
