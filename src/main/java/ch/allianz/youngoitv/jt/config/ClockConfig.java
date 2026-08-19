package ch.allianz.youngoitv.jt.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zentrale {@link Clock}-Bean, damit zeitabhaengiger Code (JWT-Ablauf, Zeitstempel) testbar bleibt,
 * statt direkt {@code new Date()}/{@code LocalDateTime.now()} zu verwenden.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
