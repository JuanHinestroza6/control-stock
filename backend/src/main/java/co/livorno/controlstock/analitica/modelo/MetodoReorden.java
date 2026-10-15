package co.livorno.controlstock.analitica.modelo;

/** Como se obtuvo el punto de reorden de una variante (ADR-009, D2). */
public enum MetodoReorden {
    /** Aproximacion normal: demanda esperada en el lead time mayor o igual a 5. */
    NORMAL,
    /** Cuantil de Poisson: demanda intermitente (menor a 5). */
    POISSON,
    /** Demanda media 0: ROP 0 (se repone al agotarse). */
    SIN_DEMANDA,
    /** Menos de la historia minima: sin ROP, el umbral es stockMinimo. */
    SIN_HISTORIA
}
