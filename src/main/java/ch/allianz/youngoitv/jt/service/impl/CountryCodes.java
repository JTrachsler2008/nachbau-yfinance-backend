package ch.allianz.youngoitv.jt.service.impl;

import java.util.Map;

/**
 * Übersetzt den ausgeschriebenen Ländernamen aus {@code SecurityInfo.country()} (z.B. "Switzerland")
 * in das zweistellige Kürzel, das {@code securities.country_code} erwartet.
 *
 * <p>Nur die Länder, in denen die Wertpapiere dieser Anwendung typischerweise notieren - eine
 * vollständige ISO-3166-Tabelle wäre für den Zweck (Länderaufteilung im Dashboard) unverhältnismässig.
 * Ein nicht gelisteter Name liefert {@code null} statt eines falschen Kürzels.</p>
 */
final class CountryCodes {

    private static final Map<String, String> ISO2_BY_NAME = Map.ofEntries(
            Map.entry("United States", "US"),
            Map.entry("Switzerland", "CH"),
            Map.entry("Germany", "DE"),
            Map.entry("France", "FR"),
            Map.entry("United Kingdom", "GB"),
            Map.entry("Ireland", "IE"),
            Map.entry("Netherlands", "NL"),
            Map.entry("Belgium", "BE"),
            Map.entry("Luxembourg", "LU"),
            Map.entry("Italy", "IT"),
            Map.entry("Spain", "ES"),
            Map.entry("Austria", "AT"),
            Map.entry("Sweden", "SE"),
            Map.entry("Norway", "NO"),
            Map.entry("Denmark", "DK"),
            Map.entry("Finland", "FI"),
            Map.entry("Japan", "JP"),
            Map.entry("China", "CN"),
            Map.entry("Hong Kong", "HK"),
            Map.entry("South Korea", "KR"),
            Map.entry("India", "IN"),
            Map.entry("Australia", "AU"),
            Map.entry("Canada", "CA"),
            Map.entry("Brazil", "BR"),
            Map.entry("Israel", "IL"));

    private CountryCodes() {
    }

    static String iso2(String countryName) {
        return countryName == null ? null : ISO2_BY_NAME.get(countryName);
    }
}
