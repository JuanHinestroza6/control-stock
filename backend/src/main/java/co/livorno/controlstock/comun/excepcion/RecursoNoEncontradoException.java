package co.livorno.controlstock.comun.excepcion;

/** Recurso inexistente (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " con id " + id + " no existe");
    }
}
