package co.livorno.controlstock.comun.excepcion;

/** Demasiados intentos fallidos (HTTP 429). */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosRestantes;

    public DemasiadosIntentosException(long segundosRestantes) {
        super("Demasiados intentos fallidos");
        this.segundosRestantes = Math.max(1, segundosRestantes);
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
