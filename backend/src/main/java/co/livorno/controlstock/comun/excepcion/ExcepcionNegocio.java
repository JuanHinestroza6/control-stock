package co.livorno.controlstock.comun.excepcion;

/** Regla de negocio violada (HTTP 422). */
public class ExcepcionNegocio extends RuntimeException {
    public ExcepcionNegocio(String mensaje) {
        super(mensaje);
    }
}
