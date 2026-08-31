package ch.allianz.youngoitv.jt.service;

/**
 * Ausgabe, Einlösung und Entwertung von Refresh-Tokens.
 *
 * Der Rohwert eines Tokens verlässt diese Schicht nur als Rückgabewert und wird nie gespeichert oder
 * geloggt - in der Datenbank steht ausschliesslich sein Hash.
 */
public interface RefreshTokenService {

    /** Neuer Token für den Benutzer. Rückgabe ist der Rohwert, der ins Cookie gehört. */
    String issue(String username);

    /**
     * Prüft den Token und tauscht ihn gegen einen neuen (Rotation).
     *
     * Rotation statt Wiederverwendung, damit ein abgefangener Token nach der ersten Einlösung wertlos
     * ist. Bei unbekanntem, abgelaufenem oder bereits entwertetem Token fliegt eine
     * {@link ch.allianz.youngoitv.jt.exception.InvalidRefreshTokenException}.
     */
    RotatedRefreshToken rotate(String rawToken);

    /**
     * Entwertet den Token beim Abmelden.
     *
     * Bewusst ohne Fehler bei unbekanntem Token: Abmelden soll auch mit einem längst ungültigen
     * Cookie zum aufgeräumten Zustand führen, statt den Benutzer in einer Sitzung festzuhalten, die
     * er beenden will.
     */
    void revoke(String rawToken);
}
