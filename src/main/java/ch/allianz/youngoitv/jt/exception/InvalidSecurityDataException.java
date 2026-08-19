package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei ungueltigen Security-Stammdaten, z.B. BOND-spezifische Felder
 * (couponRate/maturityDate) bei einem Nicht-BOND-Wertpapier.
 */
public class InvalidSecurityDataException extends RuntimeException {

    public InvalidSecurityDataException(String message) {
        super(message);
    }
}
