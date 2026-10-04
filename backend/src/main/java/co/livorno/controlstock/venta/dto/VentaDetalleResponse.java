package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record VentaDetalleResponse(Long id, String numero, LocalDateTime fecha, String vendedor, EstadoVenta estado,
        BigDecimal total, BigDecimal descuentoTotal, LocalDateTime anuladaEn, String anuladaPor,
        String motivoAnulacion, List<LineaVentaResponse> lineas, List<String> reservas) {

    /** Sin devoluciones ni reservas (venta directa recién registrada). */
    public static VentaDetalleResponse de(Venta v, boolean verCosto) {
        return de(v, verCosto, Map.of(), List.of());
    }

    /** Sin devoluciones, con las reservas de origen (números R-...). */
    public static VentaDetalleResponse de(Venta v, boolean verCosto, List<String> reservas) {
        return de(v, verCosto, Map.of(), reservas);
    }

    /** {@code devolucionesPorLinea}: devoluciones agrupadas por id de DetalleVenta. */
    public static VentaDetalleResponse de(Venta v, boolean verCosto,
            Map<Long, List<DevolucionResponse>> devolucionesPorLinea, List<String> reservas) {
        List<LineaVentaResponse> lineas = v.getDetalles().stream()
                .map(d -> LineaVentaResponse.de(d, verCosto,
                        d.getId() == null ? List.of() : devolucionesPorLinea.getOrDefault(d.getId(), List.of())))
                .toList();
        return new VentaDetalleResponse(v.getId(), v.numero(), v.getFecha(), v.getVendedor().getNombre(),
                v.getEstado(), v.getTotal(), v.descuentoTotal(), v.getAnuladaEn(),
                v.getAnuladaPor() == null ? null : v.getAnuladaPor().getNombre(), v.getMotivoAnulacion(), lineas, reservas);
    }
}
