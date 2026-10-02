package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.venta.modelo.Devolucion;
import java.time.LocalDateTime;

public record DevolucionResponse(Long id, Long detalleVentaId, int cantidad, String motivo, boolean aptaParaVenta,
        LocalDateTime fecha, String usuarioNombre) {

    public static DevolucionResponse de(Devolucion d) {
        return new DevolucionResponse(d.getId(), d.getDetalleVenta().getId(), d.getCantidad(), d.getMotivo(),
                d.isAptaParaVenta(), d.getFecha(), d.getUsuario().getNombre());
    }
}
