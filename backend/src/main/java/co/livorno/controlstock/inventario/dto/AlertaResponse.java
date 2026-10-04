package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import java.time.LocalDateTime;

public record AlertaResponse(
        Long id,
        TipoAlerta tipo,
        EstadoAlerta estado,
        Long varianteId,
        String sku,
        String productoNombre,
        Talla talla,
        String color,
        int stockDisponible,
        int umbral,
        LocalDateTime creadaEn,
        LocalDateTime revisadaEn,
        LocalDateTime atendidaEn,
        String atendidaPor) {

    /** {@code atendidaPor} null = cerrada por el sistema (o aún activa). */
    public static AlertaResponse de(Alerta a, String atendidaPor) {
        Variante v = a.getVariante();
        int umbral = v.getPuntoReorden() != null ? v.getPuntoReorden() : v.getStockMinimo();
        return new AlertaResponse(a.getId(), a.getTipo(), a.getEstado(), v.getId(), v.getSku(),
                v.getProducto().getNombre(), v.getTalla(), v.getColor(), v.getStockDisponible(), umbral,
                a.getCreadaEn(), a.getRevisadaEn(), a.getAtendidaEn(), atendidaPor);
    }
}
