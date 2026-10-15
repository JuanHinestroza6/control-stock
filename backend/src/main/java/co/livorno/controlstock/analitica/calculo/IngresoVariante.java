package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;

/**
 * Entrada del ABC: ingreso neto de una variante en la ventana (Σ (cantidad − devuelta) × precioUnitario de ventas
 * REGISTRADA; ADR-009). Nunca es negativo porque lo devuelto no supera lo vendido.
 */
public record IngresoVariante(long varianteId, BigDecimal ingresoNeto) {
}
