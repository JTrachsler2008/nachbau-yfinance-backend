package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Security;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRepository extends JpaRepository<Security, Long> {

    Optional<Security> findBySymbol(String symbol);
}
