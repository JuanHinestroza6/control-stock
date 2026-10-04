package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ReservaResponse(Long id, String numero, Long varianteId, String sku, String productoNombre, Talla talla,
        String color, int cantidad, BigDecimal precioVenta, String clienteNombre, String clienteContacto,
        EstadoReserva estado, LocalDateTime creadaEn, LocalDateTime venceEn, String creadaPor,
        LocalDateTime cerradaEn, String cerradaPor, String motivoCancelacion, Long ventaId, String ventaNumero) {

    /** {@code nombres} traduce ids de usuario a nombre; cerradaPor null = proceso del sistema. */
    public static ReservaResponse de(Reserva r, Map<Long, String> nombres) {
        var v = r.getVariante();
        var p = v.getProducto();
        return new ReservaResponse(r.getId(), r.numero(), v.getId(), v.getSku(), p.getNombre(), v.getTalla(),
                v.getColor(), r.getCantidad(), p.getPrecioVenta(), r.getClienteNombre(), r.getClienteContacto(),
                r.getEstado(), r.getCreadaEn(), r.getVenceEn(), nombres.get(r.getUsuarioId()), r.getCerradaEn(),
                r.getCerradaPor() == null ? null : nombres.get(r.getCerradaPor()), r.getMotivoCancelacion(),
                r.getVentaId(), r.getVentaId() == null ? null : String.format("V-%06d", r.getVentaId()));
    }
}
