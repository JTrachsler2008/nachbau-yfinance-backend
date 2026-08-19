package ch.allianz.youngoitv.jt.client;

import java.math.BigDecimal;

public record Snapshot(String symbol, BigDecimal price, BigDecimal changePercent) {
}
