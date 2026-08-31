package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Suche über den Hash, weil der Rohwert nie gespeichert wird. Ob der Treffer noch gültig ist
     * (abgelaufen, entwertet), entscheidet der Service - das Repository liefert nur die Zeile.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
