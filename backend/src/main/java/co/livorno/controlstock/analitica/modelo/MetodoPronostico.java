package co.livorno.controlstock.analitica.modelo;

/**
 * Método de pronóstico elegido para un producto (ADR-010, decisión 2). Se guarda en {@code analitica_producto.metodo};
 * en {@code pronostico.metodo} solo aparecen SES y HOLT_WINTERS (SIN_HISTORIA no tiene filas de pronóstico).
 */
public enum MetodoPronostico {
    /** Suavizado exponencial simple. */
    SES,
    /** Holt-Winters multiplicativo, m = 12 (siempre el multiplicativo). */
    HOLT_WINTERS,
    /** Menos de 3 meses completos: sin pronóstico; ROP y sugerencia usan la demanda histórica. */
    SIN_HISTORIA
}
