package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Portfolio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserId(Long userId);
}
