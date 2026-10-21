package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import java.math.BigDecimal;
import java.util.List;

/** Pronóstico desagregado de una variante; {@code participacion} es una fracción 0-1 del pronóstico del producto. */
public record PronosticoVarianteResponse(long varianteId, String sku, Talla talla, String color,
        BigDecimal participacion, List<PuntoPronostico> pronostico) {}
