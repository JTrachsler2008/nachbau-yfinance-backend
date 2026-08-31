package ch.allianz.youngoitv.jt.exception;

/**
 * Der vorgelegte Refresh-Token ist unbekannt, abgelaufen oder bereits entwertet.
 *
 * Antwortet mit 401, damit der Client dasselbe tut wie bei einem abgelaufenen Zugriffs-Token: neu
 * anmelden. Die Meldung nennt bewusst keinen Grund - ob ein Token unbekannt oder nur abgelaufen ist,
 * wäre für einen Angreifer eine Auskunft und für einen Benutzer ohne Nutzen.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is not valid");
    }
}
