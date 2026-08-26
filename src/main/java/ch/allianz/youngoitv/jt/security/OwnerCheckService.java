package ch.allianz.youngoitv.jt.security;

import ch.allianz.youngoitv.jt.entity.Account;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;
import org.springframework.stereotype.Service;

/**
 * Zentrale Autorisierungsprüfung in der Service-Schicht: prüft, dass ein Portfolio (und darüber
 * die Kette Account -&gt; Portfolio -&gt; User) dem authentifizierten User gehört, bevor darauf
 * lesend oder schreibend zugegriffen wird. Erweitert um den Manager-Fall aus dem User-Rollen-Plan
 * (YOUNGOITV-440): ein User mit Rolle MANAGER, der einem Portfolio als Manager zugeordnet ist, erhält
 * dieselben Rechte wie der Eigentümer - ein Admin ohne Eigentümerschaft/Manager-Zuordnung erhält
 * über diese Methode bewusst KEIN true (Admin-Rechte wirken nur auf Security-/FxRate-Endpunkte).
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
     * Strikte Eigentümer-Prüfung ohne den Manager-Fall - für Vorgänge, die bewusst nur dem
     * tatsächlichen Eigentümer vorbehalten sind (z.B. Manager-Zuweisung selbst).
     */
    public boolean isOwner(Portfolio portfolio, User principal) {
        return portfolio.getUser().getId().equals(principal.getId());
    }

    public boolean isAuthorizedForAccount(Account account, User principal) {
        return isAuthorizedForPortfolio(account.getPortfolio(), principal);
    }
}
