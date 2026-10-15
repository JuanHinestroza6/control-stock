package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.comun.excepcion.ConflictoException;

/** Ya hay una ejecucion analitica en curso (409 en el recalculo manual; la nocturna se omite). */
public class EjecucionEnCursoException extends ConflictoException {
    public EjecucionEnCursoException() {
        super("Ya hay un recálculo en curso.");
    }
}
