package co.livorno.controlstock.analitica.modelo;

/** Conteos y metrica que se guardan al terminar una ejecucion. */
public record ResultadoEjecucion(int variantesAnalizadas, int claseA, int claseB, int claseC, int conPuntoReorden,
        int alertasEvaluadas, int alertasFallidas, int variantesActivas, int enAlertaStock, int sinRotacionActivas) {
}
