package co.livorno.controlstock.analitica.servicio;

/** Conteos del cálculo ABC + punto de reorden, para {@code ejecucion_analitica}. */
public record ResultadoCalculo(int variantesAnalizadas, int claseA, int claseB, int claseC, int conPuntoReorden) {
}
