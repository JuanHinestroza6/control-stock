package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import java.util.List;

/** Celda ausente = combinación talla x color no creada. */
public record MatrizResponse(Long productoId, List<Talla> tallas, List<String> colores, List<Celda> celdas) {

    public record Celda(Talla talla, String color, Long varianteId, String sku, int stockFisico, int stockReservado,
            int stockDisponible, EstadoStock estadoStock, boolean activo) {
    }
}
