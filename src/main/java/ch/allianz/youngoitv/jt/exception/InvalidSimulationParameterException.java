package ch.allianz.youngoitv.jt.exception;

/**
 * Fachlicher Fehler bei ungueltigen Parametern fuer eine Simulation/Backtest-Anfrage
 * (Sparplan-Positionen, Symbol, Menge, Kaufdatum).
 */
public class InvalidSimulationParameterException extends RuntimeException {

    public InvalidSimulationParameterException(String message) {
        super(message);
    }
}
