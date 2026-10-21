package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;
import java.util.List;

/** Serie mensual de demanda comercial (REQ-09). Periodos {@code YYYY-MM}; totales del rango pedido; sin costos. */
public record TendenciaResponse(String desde, String hasta, Long categoriaId, Long productoId,
        List<PuntoTendencia> puntos, long totalUnidades, BigDecimal totalIngreso) {}
