package co.livorno.controlstock.analitica.modelo;

/**
 * De dónde sale la demanda del lead time (μL) en el punto de reorden de una variante (ADR-010, decisión 3). Se guarda en
 * {@code analitica_variante.fuente_demanda}.
 */
public enum FuenteDemanda {
    /** μL = Σ de la tasa diaria pronosticada de la variante en los próximos L días. */
    PRONOSTICO,
    /** Sin pronóstico del producto (nuevo, inactivo o fallo): μL = d̄ observada de 90 días × L (fórmula de la fase 5). */
    HISTORICA
}
