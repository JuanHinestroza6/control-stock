package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import java.math.BigDecimal;

/** Fila del punto de reorden: los insumos de la última ejecución más el stock actual de la variante. */
public record ReordenVarianteResponse(
        Long varianteId,
        String sku,
        String productoNombre,
        CategoriaRef categoria,
        Talla talla,
        String color,
        String claseAbc,
        int diasHistoria,
        BigDecimal demandaMedia,
        BigDecimal desviacion,
        int leadTimeDias,
        BigDecimal nivelServicio,
        BigDecimal z,
        BigDecimal stockSeguridad,
        Integer puntoReorden,
        MetodoReorden metodo,
        int stockMinimo,
        int umbralEfectivo,
        FuenteUmbral fuenteUmbral,
        int stockDisponible,
        EstadoStock estadoStock) {

    public static ReordenVarianteResponse de(AnaliticaVariante a) {
        Variante v = a.getVariante();
        Integer rop = a.getPuntoReorden();
        return new ReordenVarianteResponse(v.getId(), v.getSku(), v.getProducto().getNombre(),
                CategoriaRef.de(v.getProducto().getCategoria()), v.getTalla(), v.getColor(),
                String.valueOf(a.getClaseAbc()), a.getDiasHistoria(), a.getDemandaMedia(), a.getDesviacion(),
                a.getLeadTimeDias(), a.getNivelServicio(), a.getZ(), a.getStockSeguridad(), rop,
                a.getMetodoReorden(), v.getStockMinimo(), rop != null ? rop : v.getStockMinimo(),
                rop != null ? FuenteUmbral.ROP : FuenteUmbral.STOCK_MINIMO, v.getStockDisponible(),
                v.estadoStock());
    }
}
