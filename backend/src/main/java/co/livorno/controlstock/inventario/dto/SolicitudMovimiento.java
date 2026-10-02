package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.math.BigDecimal;

/**
 * Orden interna de movimiento (no es un DTO HTTP). {@code usuarioId} null = proceso del sistema;
 * {@code costoUnitario} solo en ENTRADA.
 */
public record SolicitudMovimiento(Long varianteId, TipoMovimiento tipo, int cantidad, BigDecimal costoUnitario,
        Long usuarioId, String referenciaTipo, Long referenciaId, String observacion) {
}
