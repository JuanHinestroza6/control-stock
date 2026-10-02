package co.livorno.controlstock.venta.dto;

import java.time.LocalDateTime;

public record DevolucionResponse(Long id, Long detalleVentaId, int cantidad, String motivo, boolean aptaParaVenta,
        LocalDateTime fecha, String usuarioNombre) {
}
