package ch.allianz.youngoitv.jt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes,
            Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMillis = expirationMinutes * 60_000;
        this.clock = clock;
    }

    public String generateToken(String username) {
        Instant now = clock.instant();
        Date issuedAt = Date.from(now);
        Date expiry = Date.from(now.plusMillis(expirationMillis));
        return Jwts.builder()
                .subject(username)
                .issuedAt(issuedAt)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the username (subject) if the token is valid and not expired, otherwise empty.
     */
    public String extractUsernameOrNull(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
