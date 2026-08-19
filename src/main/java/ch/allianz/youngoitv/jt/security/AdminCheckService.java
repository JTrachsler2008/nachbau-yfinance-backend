package ch.allianz.youngoitv.jt.security;

import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException;
import ch.allianz.youngoitv.jt.service.UserService;
import org.springframework.stereotype.Service;

/**
 * Rollenbasierte Autorisierungspruefung fuer Stammdaten-Endpunkte (Securities, FxRates), die anders
 * als der Owner-Check kein geladenes Domaenenobjekt benoetigen - nur die Rolle des aufrufenden Users.
 */
@Service
public class AdminCheckService {

    private final UserService userService;

    public AdminCheckService(UserService userService) {
        this.userService = userService;
    }

    public void requireAdmin(String username) {
        if (userService.getByUsernameOrThrow(username).getRole() != UserRole.ADMIN) {
            throw new UnauthorizedAccessException("This operation requires the ADMIN role");
        }
    }
}
