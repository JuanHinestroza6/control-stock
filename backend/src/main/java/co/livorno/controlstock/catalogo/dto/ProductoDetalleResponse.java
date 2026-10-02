package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Producto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** precioCompra es null cuando el rol del solicitante no puede ver costos (VENDEDOR). */
public record ProductoDetalleResponse(Long id, String codigo, String nombre, CategoriaRef categoria,
        BigDecimal precioCompra, BigDecimal precioVenta, int leadTimeDias, int loteMinimo, boolean activo,
        long totalVariantes, LocalDateTime creadoEn, LocalDateTime actualizadoEn) {

    public static ProductoDetalleResponse de(Producto p, boolean verCosto, long totalVariantes) {
        return new ProductoDetalleResponse(p.getId(), p.getCodigo(), p.getNombre(), CategoriaRef.de(p.getCategoria()),
                verCosto ? p.getPrecioCompra() : null, p.getPrecioVenta(), p.getLeadTimeDias(), p.getLoteMinimo(),
                p.isActivo(), totalVariantes, p.getCreadoEn(), p.getActualizadoEn());
    }
}
