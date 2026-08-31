package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.entity.RefreshToken;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.exception.InvalidRefreshTokenException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.repository.RefreshTokenRepository;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.service.RefreshTokenService;
import ch.allianz.youngoitv.jt.service.RotatedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /**
     * 32 Byte Zufall, also 256 Bit. Der Token ist reiner Zufall ohne Struktur - anders als das JWT
     * trägt er keine Aussage in sich, sondern verweist nur auf eine Zeile in {@code refresh_tokens}.
     * Damit lässt er sich serverseitig entwerten, was bei einem selbsttragenden Token nicht geht.
     */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final long expirationDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            Clock clock,
            @Value("${app.refresh-token.expiration-days}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.expirationDays = expirationDays;
    }

    @Override
    @Transactional
    public String issue(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return issueFor(user);
    }

    @Override
    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        RefreshToken token = findUsable(rawToken);

        // Erst entwerten, dann neu ausgeben: beide Schritte liegen in derselben Transaktion, damit nicht
        // ein neuer Token existiert, während der alte noch gültig ist.
        token.setRevokedAt(now());
        refreshTokenRepository.save(token);

        User user = token.getUser();
        return new RotatedRefreshToken(user.getUsername(), issueFor(user));
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private String issueFor(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = now();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plusDays(expirationDays));
        token.setCreatedAt(now);
        refreshTokenRepository.save(token);

        return rawToken;
    }

    /**
     * Liefert die Zeile zum Token oder wirft - unbekannt, abgelaufen und entwertet sind absichtlich
     * nicht unterscheidbar (siehe {@link InvalidRefreshTokenException}).
     */
    private RefreshToken findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now())) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    /**
     * SHA-256 als Hex, 64 Zeichen - passt genau auf {@code token_hash VARCHAR(64)}.
     *
     * Kein BCrypt wie beim Passwort: dort ist die absichtliche Langsamkeit der Schutz gegen Raten eines
     * menschlich gewählten Geheimnisses. Hier sind es 256 Bit Zufall, gegen die kein Wörterbuch hilft,
     * dafür muss der Hash bei jedem Refresh einmal pro Anfrage berechnet werden.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 ist in jeder Java-Plattform vorhanden; fehlt es, ist die Laufzeit defekt.
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Jetzt in der Zone des Servers, nicht in der der {@link Clock}-Bean (die läuft auf UTC).
     *
     * Alle übrigen Zeitstempel der Datenbank entstehen aus {@code LocalDateTime.now()} und stehen damit
     * in der Zone des Servers. Ein UTC-Wert in genau dieser einen Tabelle wäre zwei Stunden von allen
     * anderen entfernt, und ein Vergleich über Tabellengrenzen würde still falsch antworten. Der Umweg
     * über die Bean bleibt trotzdem, damit die Zeit im Test steuerbar ist.
     */
    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault()));
    }
}
