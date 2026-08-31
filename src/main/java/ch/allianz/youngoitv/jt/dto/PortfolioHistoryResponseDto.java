package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wertverlauf eines Portfolios und die daraus abgeleitete zeitgewichtete Rendite.
 *
 * <p>Schliesst die Lücke, die {@code PortfolioReturnsResponseDto} offen lässt: die zeitgewichtete
 * Rendite braucht eine historische Neubewertung an jedem Stichtag, und wer die einmal hat, hat
 * damit auch die Linie. Beides in einer Antwort, weil es ein Rechenweg ist - zwei Endpunkte würden
 * dieselben Kursabrufe zweimal machen und könnten sich widersprechen.</p>
 *
 * @param from angefragter Beginn des Zeitraums
 * @param to angefragtes Ende
 * @param seriesFrom erster Tag, an dem sich das Portfolio vollständig bewerten liess, und damit der
 *     Startpunkt von {@code index}. Kann nach {@code from} liegen, etwa wenn ein gehaltenes
 *     Wertpapier erst später an die Börse kam. {@code null}, wenn kein einziger Tag bewertbar war
 * @param timeWeightedReturn zeitgewichtete Rendite über {@code seriesFrom} bis {@code to}, in
 *     Prozent. {@code null}, wenn die Kette eine Lücke hat oder im Zeitraum nie Kapital investiert
 *     war - eine 0 wäre in beiden Fällen eine Aussage, die die Daten nicht stützen
 * @param benchmarkReturn Rendite der Benchmark über denselben Zeitraum, in Prozent. Gehört dazu,
 *     weil 7 % je nach Marktphase gut oder schlecht sind; ohne Bezugspunkt müsste die Oberfläche
 *     eine Einordnung behaupten, die in der Zahl nicht steckt
 * @param points der Verlauf selbst, aufsteigend nach Datum
 * @param excluded Wertpapiere, die im Zeitraum gehalten wurden, aber nicht bewertbar waren, samt
 *     Grund (dieselben Kennungen wie bei der Risikoanalyse)
 */
public record PortfolioHistoryResponseDto(
        Long portfolioId,
        String currency,
        LocalDate from,
        LocalDate to,
        LocalDate seriesFrom,
        String benchmarkSymbol,
        BigDecimal timeWeightedReturn,
        BigDecimal benchmarkReturn,
        List<PortfolioHistoryPointDto> points,
        List<RiskExclusionDto> excluded) {
}
