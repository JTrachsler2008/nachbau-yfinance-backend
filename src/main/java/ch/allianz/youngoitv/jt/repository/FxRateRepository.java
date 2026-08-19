package ch.allianz.youngoitv.jt.repository;

import ch.allianz.youngoitv.jt.entity.FxRate;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    @Query("""
            SELECT r FROM FxRate r
            WHERE r.baseCurrency = :baseCurrency AND r.quoteCurrency = :quoteCurrency
            AND r.rateDate <= :onOrBefore
            ORDER BY r.rateDate DESC LIMIT 1
            """)
    Optional<FxRate> findLatestOnOrBefore(
            @Param("baseCurrency") String baseCurrency,
            @Param("quoteCurrency") String quoteCurrency,
            @Param("onOrBefore") LocalDate onOrBefore);
}
