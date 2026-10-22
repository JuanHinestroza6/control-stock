package co.livorno.controlstock.comun.exportacion;

/** Tipo de dato de una columna; define el formato en Excel y en PDF. */
public enum TipoColumna {
    TEXTO,
    ENTERO,
    /** {@code BigDecimal} con escala 2. */
    DINERO,
    /** Número ya expresado en puntos porcentuales (12,3 significa 12,3 %). */
    PORCENTAJE,
    /** {@code LocalDate}. */
    FECHA,
    /** {@code LocalDateTime}. */
    FECHA_HORA
}
