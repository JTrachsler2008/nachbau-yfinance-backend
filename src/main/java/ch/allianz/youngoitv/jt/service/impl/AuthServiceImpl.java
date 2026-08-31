package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.LoginRequestDto;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.security.JwtService;
import ch.allianz.youngoitv.jt.service.AuthService;
import ch.allianz.youngoitv.jt.service.AuthTokens;
import ch.allianz.youngoitv.jt.service.RefreshTokenService;
import ch.allianz.youngoitv.jt.service.RotatedRefreshToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public AuthTokens login(LoginRequestDto request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return new AuthTokens(
                jwtService.generateToken(user.getUsername()),
                refreshTokenService.issue(user.getUsername()));
    }

    @Override
    public AuthTokens refresh(String rawRefreshToken) {
        // Kein Passwort und keine Benutzerprüfung mehr: der eingelöste Token IST der Nachweis. Deshalb
        // wird er dabei rotiert - siehe RefreshTokenService.rotate.
        RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        return new AuthTokens(jwtService.generateToken(rotated.username()), rotated.rawToken());
    }

    @Override
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
