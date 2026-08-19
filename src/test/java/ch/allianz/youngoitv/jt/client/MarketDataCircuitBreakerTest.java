package ch.allianz.youngoitv.jt.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MarketDataCircuitBreakerTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock mutableClock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void staysClosedBeforeThresholdIsReached() {
        MarketDataCircuitBreaker breaker = new MarketDataCircuitBreaker(3, 30, mutableClock);

        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void opensAfterConsecutiveFailuresReachThreshold() {
        MarketDataCircuitBreaker breaker = new MarketDataCircuitBreaker(3, 30, mutableClock);

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.isOpen()).isTrue();
    }

    @Test
    void successResetsFailureCount() {
        MarketDataCircuitBreaker breaker = new MarketDataCircuitBreaker(3, 30, mutableClock);

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void closesAgainAfterOpenDurationElapses() {
        MarketDataCircuitBreaker breaker = new MarketDataCircuitBreaker(3, 30, mutableClock);
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.isOpen()).isTrue();

        now.set(now.get().plusSeconds(31));

        assertThat(breaker.isOpen()).isFalse();
    }
}
