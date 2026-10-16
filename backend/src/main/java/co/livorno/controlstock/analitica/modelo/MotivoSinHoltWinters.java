package co.livorno.controlstock.analitica.modelo;

/**
 * Por qué Holt-Winters multiplicativo no compite para un producto y se usa SES (ADR-010, decisión 2). Se guarda en
 * {@code analitica_producto.motivo_sin_hw}.
 */
public enum MotivoSinHoltWinters {
    /** Historia insuficiente: menos de dos temporadas en el entrenamiento (o menos de 30 meses en la selección). */
    POCA_HISTORIA,
    /** Algún mes de la serie corregida es 0: un índice estacional 0 haría inestable y / s. */
    MES_EN_CERO,
    /** El nivel inicial no es positivo en el primer año, o todas las combinaciones de la rejilla llevan el nivel a ≤ 0. */
    NIVEL_NO_POSITIVO
}
