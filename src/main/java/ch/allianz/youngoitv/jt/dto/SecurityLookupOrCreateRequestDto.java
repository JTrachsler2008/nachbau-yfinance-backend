package ch.allianz.youngoitv.jt.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Eingabe für das Anlegen eines Wertpapiers aus der Live-Suche im Kaufformular heraus.
 *
 * <p>Bewusst nur das Symbol: Name, Anlageart, Handelswährung, Sektor und Land kommen serverseitig
 * vom Marktdatenanbieter. Ein Client könnte hier sonst beliebige Stammdaten unterschieben, die dann
 * ungeprüft in der für alle Benutzer geteilten Wertpapierliste landen.</p>
 */
public record SecurityLookupOrCreateRequestDto(@NotBlank String symbol) {
}
