package co.livorno.controlstock.comun.excepcion;

/** Conflicto con el estado actual del recurso (HTTP 409). */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
