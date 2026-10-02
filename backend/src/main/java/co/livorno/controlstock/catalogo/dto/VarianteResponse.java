package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;

public record VarianteResponse(Long id, Long productoId, String sku, Talla talla, String color, int stockFisico,
        int stockReservado, int stockDisponible, int stockMinimo, Integer puntoReorden, String claseAbc,
        EstadoStock estadoStock, boolean activo) {

    public static VarianteResponse de(Variante v) {
        return new VarianteResponse(v.getId(), v.getProducto().getId(), v.getSku(), v.getTalla(), v.getColor(),
                v.getStockFisico(), v.getStockReservado(), v.getStockDisponible(), v.getStockMinimo(),
                v.getPuntoReorden(), v.getClaseAbc() != null ? v.getClaseAbc().toString() : null,
                v.estadoStock(), v.isActivo());
    }
}
