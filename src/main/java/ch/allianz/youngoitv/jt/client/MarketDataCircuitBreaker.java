package ch.allianz.youngoitv.jt.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Einfacher, selbst implementierter Circuit Breaker (statt Resilience4j, dessen Spring-Boot-3-
 * Integration zum Zeitpunkt der Umsetzung nicht sicher mit Spring Boot 4.0.7 kompatibel war).
 * Oeffnet nach N aufeinanderfolgenden Fehlschlaegen des primaeren Providers und schliesst nach
 * einer konfigurierbaren Wartezeit wieder (Half-Open: der naechste Aufruf nach Ablauf der Wartezeit
 * wird wieder auf den primaeren Provider geroutet).
 */
@Component
public class MarketDataCircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openedAt;

    public MarketDataCircuitBreaker(
            @Value("${app.market-data.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${app.market-data.circuit-breaker.open-duration-seconds:30}") long openDurationSeconds,
            Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openDurationSeconds);
        this.clock = clock;
    }

    public boolean isOpen() {
        Instant openedAtSnapshot = openedAt;
        if (openedAtSnapshot == null) {
            return false;
        }
        if (clock.instant().isAfter(openedAtSnapshot.plus(openDuration))) {
            reset();
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        reset();
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAt = clock.instant();
        }
    }

    private void reset() {
        consecutiveFailures.set(0);
        openedAt = null;
    }
}
