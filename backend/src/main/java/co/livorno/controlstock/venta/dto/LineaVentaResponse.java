package co.livorno.controlstock.venta.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.venta.modelo.DetalleVenta;
import java.math.BigDecimal;
import java.util.List;

public record LineaVentaResponse(Long detalleId, Long varianteId, String sku, String productoNombre, Talla talla,
        String color, int cantidad, BigDecimal precioLista, BigDecimal precioUnitario, BigDecimal costoUnitario,
        BigDecimal subtotal, int cantidadDevuelta, List<DevolucionResponse> devoluciones) {

    /** {@code verCosto = false} (rol VENDEDOR) deja el costo en null. */
    public static LineaVentaResponse de(DetalleVenta d, boolean verCosto, List<DevolucionResponse> devoluciones) {
        int devuelto = devoluciones.stream().mapToInt(DevolucionResponse::cantidad).sum();
        return new LineaVentaResponse(d.getId(), d.getVariante().getId(), d.getVariante().getSku(),
                d.getVariante().getProducto().getNombre(), d.getVariante().getTalla(), d.getVariante().getColor(),
                d.getCantidad(), d.getPrecioLista(), d.getPrecioUnitario(), verCosto ? d.getCostoUnitario() : null,
                d.subtotal(), devuelto, devoluciones);
    }
}
