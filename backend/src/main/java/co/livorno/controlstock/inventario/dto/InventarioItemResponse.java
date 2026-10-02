package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import java.math.BigDecimal;

/** Fila del inventario actual. Sin costos: lo ven todos los roles autenticados. */
public record InventarioItemResponse(
        Long varianteId,
        String sku,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        CategoriaRef categoria,
        Talla talla,
        String color,
        int stockFisico,
        int stockReservado,
        int stockDisponible,
        int stockMinimo,
        Integer puntoReorden,
        EstadoStock estadoStock,
        BigDecimal precioVenta,
        boolean activo) {

    public static InventarioItemResponse de(Variante v) {
        return new InventarioItemResponse(v.getId(), v.getSku(), v.getProducto().getId(),
                v.getProducto().getCodigo(), v.getProducto().getNombre(),
                CategoriaRef.de(v.getProducto().getCategoria()), v.getTalla(), v.getColor(), v.getStockFisico(),
                v.getStockReservado(), v.getStockDisponible(), v.getStockMinimo(), v.getPuntoReorden(),
                v.estadoStock(), v.getProducto().getPrecioVenta(), v.isActivo());
    }
}
