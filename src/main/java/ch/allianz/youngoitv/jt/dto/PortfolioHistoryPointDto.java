package ch.allianz.youngoitv.jt.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ein Tag des Wertverlaufs.
 *
 * @param date Handelstag; zusätzlich sind Buchungstage und die Ränder des Zeitraums enthalten
 * @param value Marktwert der an diesem Tag gehaltenen Wertpapiere, in der Basiswährung des
 *     Portfolios. {@code null}, wenn für ein gehaltenes Wertpapier an diesem Tag kein Kurs oder kein
 *     Wechselkurs vorliegt: die Summe der übrigen wäre kein Marktwert des Portfolios, sondern eine
 *     kleinere Zahl, die von einem echten Rückgang nicht zu unterscheiden ist
 * @param invested kumulierter Nettoeinsatz bis einschliesslich dieses Tags, in der Basiswährung.
 *     Käufe erhöhen ihn, Verkäufe und Dividenden senken ihn. Zusammen mit {@code value} ist das der
 *     Gewinn oder Verlust als Abstand zweier Linien, ohne dass die Oberfläche rechnen muss
 * @param index zeitgewichtete Wertentwicklung, Basis 100 am ersten Punkt der Reihe. Anders als
 *     {@code value} von Ein- und Auszahlungen bereinigt und deshalb das, was mit einer Benchmark
 *     vergleichbar ist. {@code null} ab dem ersten Tag, an dem die Kette eine Lücke hat
 * @param benchmarkIndex Kursverlauf der Benchmark, auf denselben Startpunkt normiert.
 *     {@code null}, wenn für die Benchmark keine Historie vorliegt
 */
public record PortfolioHistoryPointDto(
        LocalDate date,
        BigDecimal value,
        BigDecimal invested,
        BigDecimal index,
        BigDecimal benchmarkIndex) {
}
