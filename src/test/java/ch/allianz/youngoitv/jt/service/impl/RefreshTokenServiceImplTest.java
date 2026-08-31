package ch.allianz.youngoitv.jt.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.allianz.youngoitv.jt.entity.RefreshToken;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.exception.InvalidRefreshTokenException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.repository.RefreshTokenRepository;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Deckt ab, was der Endpunkt-Test nicht erreicht: das Ablaufen eines Tokens und die Zusicherung, dass
 * nur der Hash in die Datenbank geht.
 */
class RefreshTokenServiceImplTest {

    private static final long EXPIRATION_DAYS = 14;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock mutableClock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void issueStoresTheHashAndNotTheToken() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userNamed("alice")));
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        String rawToken = service.issue("alice");

        RefreshToken saved = captureSaved(refreshTokenRepository);
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        // SHA-256 als Hex: 64 Zeichen, genau die Breite von token_hash.
        assertThat(saved.getTokenHash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(saved.getExpiresAt()).isEqualTo(localNow().plusDays(EXPIRATION_DAYS));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void issueForAnUnknownUserFails() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        assertThatThrownBy(() -> service.issue("nobody")).isInstanceOf(ResourceNotFoundException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(userNamed("bob")));
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        String rawToken = service.issue("bob");
        RefreshToken stored = captureSaved(refreshTokenRepository);
        when(refreshTokenRepository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        now.set(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(EXPIRATION_DAYS * 86400 + 1));

        assertThatThrownBy(() -> service.rotate(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateAcceptsTheTokenOnItsLastDay() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(userNamed("carol")));
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        String rawToken = service.issue("carol");
        RefreshToken stored = captureSaved(refreshTokenRepository);
        when(refreshTokenRepository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        now.set(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(EXPIRATION_DAYS * 86400 - 1));

        assertThat(service.rotate(rawToken).username()).isEqualTo("carol");
        assertThat(stored.getRevokedAt()).isEqualTo(localNow());
    }

    @Test
    void revokeIgnoresAnUnknownToken() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        service.revoke("not-a-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeWithoutATokenTouchesNothing() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenServiceImpl service = serviceWith(refreshTokenRepository, userRepository);

        service.revoke(null);
        service.revoke("  ");

        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshTokenServiceImpl serviceWith(
            RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        return new RefreshTokenServiceImpl(refreshTokenRepository, userRepository, mutableClock, EXPIRATION_DAYS);
    }

    private RefreshToken captureSaved(RefreshTokenRepository refreshTokenRepository) {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(now.get(), ZoneOffset.UTC);
    }

    private User userNamed(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }
}
