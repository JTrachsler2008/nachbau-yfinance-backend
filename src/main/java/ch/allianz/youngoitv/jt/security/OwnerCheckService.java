package ch.allianz.youngoitv.jt.security;

import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;
import org.springframework.stereotype.Service;

/**
 * Zentrale Autorisierungspruefung in der Service-Schicht: prueft, dass ein Portfolio (und darueber
 * die Kette Account -&gt; Portfolio -&gt; User) dem authentifizierten User gehoert, bevor darauf
 * lesend oder schreibend zugegriffen wird. Erweitert um den Manager-Fall aus dem User-Rollen-Plan
 * (YOUNGOITV-440): ein User mit Rolle MANAGER, der einem Portfolio als Manager zugeordnet ist, erhaelt
 * dieselben Rechte wie der Eigentuemer - ein Admin ohne Eigentuemerschaft/Manager-Zuordnung erhaelt
 * ueber diese Methode bewusst KEIN true (Admin-Rechte wirken nur auf Security-/FxRate-Endpunkte).
 */
@Service
public class OwnerCheckService {

    public boolean isAuthorizedForPortfolio(Portfolio portfolio, User principal) {
        if (isOwner(portfolio, principal)) {
            return true;
        }
        return principal.getRole() == UserRole.MANAGER
                && portfolio.getManager() != null
                && principal.getId().equals(portfolio.getManager().getId());
    }

    /**
     * Strikte Eigentuemer-Prüfung ohne den Manager-Fall - fuer Vorgaenge, die bewusst nur dem
     * tatsaechlichen Eigentuemer vorbehalten sind (z.B. Manager-Zuweisung selbst).
     */
    public boolean isOwner(Portfolio portfolio, User principal) {
        return portfolio.getUser().getId().equals(principal.getId());
    }

    public boolean isAuthorizedForAccount(Account account, User principal) {
        return isAuthorizedForPortfolio(account.getPortfolio(), principal);
    }
}
