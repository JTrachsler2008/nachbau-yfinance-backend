package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.LoginRequestDto;

public interface AuthService {

    AuthTokens login(LoginRequestDto request);

    /**
     * Tauscht einen gültigen Refresh-Token gegen ein frisches Zugriffs-Token und einen neuen
     * Refresh-Token. Wirft {@link ch.allianz.youngoitv.jt.exception.InvalidRefreshTokenException},
     * wenn der vorgelegte Token nicht einlösbar ist.
     */
    AuthTokens refresh(String rawRefreshToken);

    /** Entwertet den Refresh-Token. Ohne Fehler, auch wenn er schon ungültig war. */
    void logout(String rawRefreshToken);
}
