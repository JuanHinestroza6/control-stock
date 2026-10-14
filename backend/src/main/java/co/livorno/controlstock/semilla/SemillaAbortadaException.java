package co.livorno.controlstock.semilla;

/** La carga de datos semilla no puede continuar (protección o configuración inválida); no se guarda nada. */
public class SemillaAbortadaException extends RuntimeException {

    public SemillaAbortadaException(String mensaje) {
        super(mensaje);
    }
}
