package ch.allianz.youngoitv.jt.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Baut das Cookie, in dem der Refresh-Token zum Browser und zurück reist.
 *
 * Die Eigenschaften sind der eigentliche Schutz und stehen deshalb an einer Stelle statt in jedem
 * Endpunkt einzeln:
 * <ul>
 *   <li>{@code httpOnly}: JavaScript kann das Cookie nicht lesen. Genau darum liegt der Token hier und
 *       nicht im localStorage (SEC-2).</li>
 *   <li>{@code SameSite=Strict}: der Browser schickt das Cookie nur bei Aufrufen von der eigenen Seite.
 *       Damit kann eine fremde Seite keinen Refresh im Namen des Benutzers auslösen - das ersetzt den
 *       CSRF-Schutz, den die zustandslose Konfiguration nicht hat.</li>
 *   <li>{@code Path=/}: der Browser sieht die Endpunkte hinter dem Dev-Proxy als {@code /api/auth/...},
 *       das Backend selbst als {@code /auth/...}. Ein engerer Pfad würde in einer der beiden Sichten
 *       nicht mehr passen und das Cookie stillschweigend nicht mitsenden.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookieFactory(
            @Value("${app.refresh-token.cookie-secure}") boolean secure,
            @Value("${app.refresh-token.expiration-days}") long expirationDays) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(expirationDays);
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(maxAge).build();
    }

    /**
     * Löscht das Cookie beim Abmelden. {@code maxAge=0} ist der vorgesehene Weg dafür; Name, Pfad und
     * Flags müssen mit {@link #create(String)} übereinstimmen, sonst legt der Browser ein zweites
     * Cookie an, statt das bestehende zu ersetzen.
     */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
