package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler, wenn der authentifizierte User auf ein fremdes Domänenobjekt zugreifen will
 * (Owner-Check verletzt, siehe OwnerCheckService).
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
