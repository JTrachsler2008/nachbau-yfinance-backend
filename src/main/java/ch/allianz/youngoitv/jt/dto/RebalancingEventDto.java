package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record RebalancingEventDto(
        LocalDate month, String reason, BigDecimal portfolioValueBefore, Map<String, BigDecimal> trades) {
}
