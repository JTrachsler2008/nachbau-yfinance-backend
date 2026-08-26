package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei einer ungültigen Rollenzuweisung, z.B. Manager-Zuordnung auf einen User
 * ohne Rolle MANAGER.
 */
public class InvalidRoleAssignmentException extends RuntimeException {

    public InvalidRoleAssignmentException(String message) {
        super(message);
    }
}
