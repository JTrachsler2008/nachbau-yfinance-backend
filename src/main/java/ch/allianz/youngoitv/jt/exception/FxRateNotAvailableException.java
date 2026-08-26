package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler, wenn für eine FX-Umrechnung kein passender Wechselkurs gefunden wird
 * (Fallback-Strategie noch offen, siehe fachlicher Plan "Umgang mit historischen FX-Kurslücken").
 */
public class FxRateNotAvailableException extends RuntimeException {

    public FxRateNotAvailableException(String message) {
        super(message);
    }
}
