package ch.allianz.youngoitv.jt.service;

/**
 * Das Paar, das eine Anmeldung ergibt: das kurzlebige Zugriffs-Token für die Antwort und der
 * langlebige Refresh-Token für das Cookie.
 *
 * Beide zusammen in einem Rückgabewert, weil sie immer gemeinsam entstehen - ein Aufrufer, der nur
 * eines davon holt, hätte eine halbe Sitzung.
 */
public record AuthTokens(String accessToken, String refreshToken) {
}
