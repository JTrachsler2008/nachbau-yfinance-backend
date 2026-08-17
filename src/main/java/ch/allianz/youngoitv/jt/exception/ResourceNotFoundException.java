package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler, wenn ein angefordertes Domaenenobjekt (z.B. Portfolio, Account) nicht existiert.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
