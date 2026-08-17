package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei unbekanntem oder fuer den Kontext ungueltigem Transaktionstyp.
 */
public class InvalidTransactionTypeException extends RuntimeException {

    public InvalidTransactionTypeException(String message) {
        super(message);
    }
}
