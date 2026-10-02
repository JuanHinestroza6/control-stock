package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaResumenResponse(Long id, String numero, LocalDateTime fecha, String vendedor, int unidades,
        BigDecimal total, EstadoVenta estado) {

    public static VentaResumenResponse de(Venta v) {
        return new VentaResumenResponse(v.getId(), v.numero(), v.getFecha(), v.getVendedor().getNombre(),
                v.unidades(), v.getTotal(), v.getEstado());
    }
}
