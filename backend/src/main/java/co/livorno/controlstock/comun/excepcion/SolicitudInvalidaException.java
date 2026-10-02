package co.livorno.controlstock.comun.excepcion;

/** Solicitud mal formada que no es un error de validación de campo (HTTP 400). */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
