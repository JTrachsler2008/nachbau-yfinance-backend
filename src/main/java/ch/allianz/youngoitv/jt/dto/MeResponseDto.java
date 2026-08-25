package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.UserRole;

/**
 * Angaben zum angemeldeten Benutzer.
 *
 * <p>Die Rolle gehört hierher und nicht in das Token: die Oberfläche muss rollenabhängige Bereiche
 * (Verwaltung von Wertpapieren, Managerzuordnung) ein- und ausblenden, und sie soll dafür nicht das
 * JWT auslesen müssen. Die Rolle ist reine Anzeigehilfe, die eigentliche Prüfung bleibt im Backend.</p>
 */
public record MeResponseDto(String username, UserRole role) {
}
