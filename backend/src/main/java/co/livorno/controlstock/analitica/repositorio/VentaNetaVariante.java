package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.calculo.IngresoVariante;
import java.math.BigDecimal;

/**
 * Venta neta comercial de una variante en un rango (ADR-009, R9): unidades = vendidas − devueltas (todas), ingreso =
 * Σ (cantidad − devuelta) × precioUnitario, escala 2.
 */
public record VentaNetaVariante(long varianteId, long unidadesNetas, BigDecimal ingresoNeto) {

    /** Entrada del clasificador ABC. */
    public IngresoVariante aIngreso() {
        return new IngresoVariante(varianteId, ingresoNeto);
    }
}
