package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei SELL ohne genuegend Bestand oder Auszahlung ohne genuegend Cash.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
