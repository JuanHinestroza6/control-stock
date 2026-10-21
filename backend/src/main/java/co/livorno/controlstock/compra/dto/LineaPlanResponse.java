package co.livorno.controlstock.compra.dto;

import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.compra.modelo.DetallePlan;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record LineaPlanResponse(
        Long id,
        Long varianteId,
        String sku,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        Talla talla,
        String color,
        String claseAbc,
        Integer cantidadSugerida,
        int cantidadAprobada,
        BigDecimal costoEstimado,
        BigDecimal subtotal,
        String justificacion,
        Integer cantidadRecibida,
        BigDecimal costoRecibido,
        int disponibleActual,
        long enCaminoActual,
        boolean varianteActiva) {

    public static LineaPlanResponse de(DetallePlan d, long enCamino) {
        Variante v = d.getVariante();
        Producto p = v.getProducto();
        BigDecimal subtotal = d.getCostoEstimado().multiply(BigDecimal.valueOf(d.getCantidadAprobada()))
                .setScale(2, RoundingMode.HALF_UP);
        return new LineaPlanResponse(d.getId(), v.getId(), v.getSku(), p.getId(), p.getCodigo(), p.getNombre(),
                v.getTalla(), v.getColor(), v.getClaseAbc() == null ? null : v.getClaseAbc().toString(),
                d.getCantidadSugerida(), d.getCantidadAprobada(), d.getCostoEstimado(), subtotal,
                d.getJustificacion(), d.getCantidadRecibida(), d.getCostoRecibido(), v.getStockDisponible(), enCamino,
                v.isActivo() && p.isActivo());
    }
}
