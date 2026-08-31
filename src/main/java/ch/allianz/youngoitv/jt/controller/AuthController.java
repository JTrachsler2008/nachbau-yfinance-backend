package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.LoginRequestDto;
import ch.allianz.youngoitv.jt.dto.LoginResponseDto;
import ch.allianz.youngoitv.jt.security.RefreshTokenCookieFactory;
import ch.allianz.youngoitv.jt.service.AuthService;
import ch.allianz.youngoitv.jt.service.AuthTokens;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    public AuthController(AuthService authService, RefreshTokenCookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return withRefreshCookie(authService.login(request));
    }

    /**
     * Erneuert das Zugriffs-Token allein aus dem Cookie.
     *
     * Der Token wird absichtlich nicht als Feld im Body erwartet: läge er dort, müsste JavaScript ihn
     * kennen, und der httpOnly-Schutz wäre umsonst.
     *
     * Fehlt das Cookie, ist das kein Sonderfall - der Service behandelt es wie einen ungültigen Token
     * und antwortet mit 401. Der aufrufende Browser hat dann noch keine Sitzung, was beim ersten
     * Seitenaufruf ohne Anmeldung der Normalfall ist.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    /**
     * Entwertet den Refresh-Token serverseitig und löscht das Cookie.
     *
     * 204 statt eines Bodys, weil es nichts zu berichten gibt: der Aufruf gelingt auch mit einem
     * längst ungültigen Cookie. Das Zugriffs-Token verfällt von allein, es kann nicht widerrufen
     * werden - entscheidend ist, dass sich daraus kein neues mehr holen lässt.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<LoginResponseDto> withRefreshCookie(AuthTokens tokens) {
        ResponseCookie cookie = cookieFactory.create(tokens.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponseDto(tokens.accessToken()));
    }
}
