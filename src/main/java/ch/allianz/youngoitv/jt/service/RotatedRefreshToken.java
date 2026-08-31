package ch.allianz.youngoitv.jt.service;

/**
 * Ergebnis einer Token-Rotation: der Benutzer, für den ein neues Zugriffs-Token ausgestellt werden
 * darf, und der neue Refresh-Token, der das alte Cookie ersetzt.
 */
public record RotatedRefreshToken(String username, String rawToken) {
}
