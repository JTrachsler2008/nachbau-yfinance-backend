package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler, wenn bei der Registrierung ein bereits vergebener Username oder eine bereits
 * registrierte Email verwendet wird.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
