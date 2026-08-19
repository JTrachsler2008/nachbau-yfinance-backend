package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Position;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByAccountId(Long accountId);

    Optional<Position> findByAccountIdAndSecurityId(Long accountId, Long securityId);

    List<Position> findByAccountPortfolioId(Long portfolioId);
}
