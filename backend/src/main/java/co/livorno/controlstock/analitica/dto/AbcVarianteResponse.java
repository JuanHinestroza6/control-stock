package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fila de la clasificación ABC. Participación y acumulado en porcentaje (0–100, escala 2). */
public record AbcVarianteResponse(
        Long varianteId,
        String sku,
        Long productoId,
        String productoNombre,
        CategoriaRef categoria,
        Talla talla,
        String color,
        int posicion,
        int unidadesNetas,
        BigDecimal ingresoNeto,
        BigDecimal participacion,
        BigDecimal acumulado,
        String claseAbc) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public static AbcVarianteResponse de(AnaliticaVariante a) {
        Variante v = a.getVariante();
        return new AbcVarianteResponse(v.getId(), v.getSku(), v.getProducto().getId(), v.getProducto().getNombre(),
                CategoriaRef.de(v.getProducto().getCategoria()), v.getTalla(), v.getColor(), a.getPosicion(),
                a.getUnidadesNetas(), a.getIngresoNeto(), porcentaje(a.getParticipacion()),
                porcentaje(a.getAcumulado()), String.valueOf(a.getClaseAbc()));
    }

    /** Fracción (escala 6) a porcentaje (escala 2, HALF_UP). */
    public static BigDecimal porcentaje(BigDecimal fraccion) {
        return fraccion.multiply(CIEN).setScale(2, RoundingMode.HALF_UP);
    }
}
