package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record VentaDetalleResponse(Long id, String numero, LocalDateTime fecha, String vendedor, EstadoVenta estado,
        BigDecimal total, BigDecimal descuentoTotal, LocalDateTime anuladaEn, String anuladaPor,
        String motivoAnulacion, List<LineaVentaResponse> lineas) {

    /** Sin devoluciones (venta recién registrada o anulada). */
    public static VentaDetalleResponse de(Venta v, boolean verCosto) {
        return de(v, verCosto, Map.of());
    }

    /** {@code devolucionesPorLinea}: devoluciones agrupadas por id de DetalleVenta. */
    public static VentaDetalleResponse de(Venta v, boolean verCosto,
            Map<Long, List<DevolucionResponse>> devolucionesPorLinea) {
        List<LineaVentaResponse> lineas = v.getDetalles().stream()
                .map(d -> LineaVentaResponse.de(d, verCosto,
                        d.getId() == null ? List.of() : devolucionesPorLinea.getOrDefault(d.getId(), List.of())))
                .toList();
        return new VentaDetalleResponse(v.getId(), v.numero(), v.getFecha(), v.getVendedor().getNombre(),
                v.getEstado(), v.getTotal(), v.descuentoTotal(), v.getAnuladaEn(),
                v.getAnuladaPor() == null ? null : v.getAnuladaPor().getNombre(), v.getMotivoAnulacion(), lineas);
    }
}
