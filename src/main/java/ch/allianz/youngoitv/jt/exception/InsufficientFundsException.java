package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei SELL ohne genügend Bestand oder Auszahlung ohne genügend Cash.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
