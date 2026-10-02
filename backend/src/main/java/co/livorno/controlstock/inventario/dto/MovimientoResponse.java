package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoResponse(
        Long id,
        LocalDateTime fecha,
        TipoMovimiento tipo,
        int cantidad,
        int fisicoAntes,
        int fisicoDespues,
        int reservadoAntes,
        int reservadoDespues,
        BigDecimal costoUnitario,
        Long varianteId,
        String sku,
        String productoNombre,
        String usuarioNombre,
        String referenciaTipo,
        Long referenciaId,
        String observacion) {

    /** {@code usuarioNombre} null = movimiento del sistema. */
    public static MovimientoResponse de(Movimiento m, String usuarioNombre) {
        return new MovimientoResponse(m.getId(), m.getFecha(), m.getTipo(), m.getCantidad(), m.getFisicoAntes(),
                m.getFisicoDespues(), m.getReservadoAntes(), m.getReservadoDespues(), m.getCostoUnitario(),
                m.getVariante().getId(), m.getVariante().getSku(), m.getVariante().getProducto().getNombre(),
                usuarioNombre, m.getReferenciaTipo(), m.getReferenciaId(), m.getObservacion());
    }
}
