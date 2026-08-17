package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
