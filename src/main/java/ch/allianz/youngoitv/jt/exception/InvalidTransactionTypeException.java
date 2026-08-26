package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei unbekanntem oder für den Kontext ungültigem Transaktionstyp.
 */
public class InvalidTransactionTypeException extends RuntimeException {

    public InvalidTransactionTypeException(String message) {
        super(message);
    }
}
