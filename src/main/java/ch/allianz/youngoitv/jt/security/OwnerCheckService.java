package ch.allianz.youngoitv.jt.security;

import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import org.springframework.stereotype.Service;

/**
 * Zentrale Autorisierungspruefung in der Service-Schicht: prueft, dass ein Portfolio (und darueber
 * die Kette Account -&gt; Portfolio -&gt; User) dem authentifizierten User gehoert, bevor darauf
 * lesend oder schreibend zugegriffen wird. Wird in YOUNGOITV-432 (Owner-Check-Erweiterung fuer
 * Manager-Zugriff) um den Manager-Fall aus dem User-Rollen-Plan erweitert - hier zunaechst nur der
 * einfache Eigentuemer-Check.
 */
@Service
public class OwnerCheckService {

    public boolean isAuthorizedForPortfolio(Portfolio portfolio, User principal) {
        return portfolio.getUser().getId().equals(principal.getId());
    }
}
