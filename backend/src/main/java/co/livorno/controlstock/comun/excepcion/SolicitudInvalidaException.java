package co.livorno.controlstock.comun.excepcion;

/**
 * Solicitud mal formada que no es un error de validación de campo estándar (HTTP 400). Si se indica el campo, la
 * respuesta incluye {@code errores: [{campo, mensaje}]}.
 */
public class SolicitudInvalidaException extends RuntimeException {

    private final String campo;

    public SolicitudInvalidaException(String mensaje) {
        this(null, mensaje);
    }

    public SolicitudInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
