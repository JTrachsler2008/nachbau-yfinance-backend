package ch.allianz.youngoitv.jt.client;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(String symbol, BigDecimal price, String currency, Instant asOf) {
}
