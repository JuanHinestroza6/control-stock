package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaDetalleResponse(Long id, String numero, LocalDateTime fecha, String vendedor, EstadoVenta estado,
        BigDecimal total, BigDecimal descuentoTotal, LocalDateTime anuladaEn, String anuladaPor,
        String motivoAnulacion, List<LineaVentaResponse> lineas) {

    public static VentaDetalleResponse de(Venta v, boolean verCosto) {
        List<LineaVentaResponse> lineas = v.getDetalles().stream()
                .map(d -> LineaVentaResponse.de(d, verCosto, List.of())).toList();
        return new VentaDetalleResponse(v.getId(), v.numero(), v.getFecha(), v.getVendedor().getNombre(),
                v.getEstado(), v.getTotal(), v.descuentoTotal(), v.getAnuladaEn(),
                v.getAnuladaPor() == null ? null : v.getAnuladaPor().getNombre(), v.getMotivoAnulacion(), lineas);
    }
}
