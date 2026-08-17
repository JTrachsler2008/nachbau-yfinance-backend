package ch.allianz.youngoitv.jt.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Stabile Fehlerstruktur fuer alle Fehlerantworten (fachlich und technisch). fieldErrors ist nur bei
 * Bean-Validation-Fehlern befuellt (Feldname -> Fehlermeldung), sonst null.
 */
public record ErrorResponseDto(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {

    public ErrorResponseDto(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message, null);
    }

    public ErrorResponseDto(int status, String error, String message, Map<String, String> fieldErrors) {
        this(LocalDateTime.now(), status, error, message, fieldErrors);
    }
}
