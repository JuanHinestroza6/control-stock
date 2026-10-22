package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila de rentabilidad. id es el de la categoría, producto o variante según la agrupación; codigo es el código del
 * producto o el SKU (null al agrupar por categoría); categoria es null al agrupar por categoría. participacionMargen
 * (escala 1, porcentaje del margen bruto total) es null si el margen total es 0.
 */
public record FilaRentabilidad(Long id, String codigo, String nombre, String categoria, BigDecimal ventasLista,
        BigDecimal descuentos, BigDecimal facturado, BigDecimal devoluciones, BigDecimal ingresoNeto,
        BigDecimal costoVentas, BigDecimal margenBruto, BigDecimal margenPorcentaje, BigDecimal mermaNoApta,
        BigDecimal resultadoDespuesMermas, long unidadesVendidas, long unidadesDevueltas, long unidadesNetas,
        BigDecimal participacionMargen) {}
