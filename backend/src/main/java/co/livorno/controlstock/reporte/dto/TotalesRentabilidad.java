package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Totales del periodo (ADR-011): ventas a lista, descuentos, facturado, devoluciones, ingreso neto, costo de ventas,
 * margen bruto (neto menos costo) y merma no apta. margenPorcentaje (escala 1) es null si el ingreso neto es 0.
 */
public record TotalesRentabilidad(BigDecimal ventasLista, BigDecimal descuentos, BigDecimal facturado,
        BigDecimal devoluciones, BigDecimal ingresoNeto, BigDecimal costoVentas, BigDecimal margenBruto,
        BigDecimal margenPorcentaje, BigDecimal mermaNoApta, BigDecimal resultadoDespuesMermas,
        long unidadesVendidas, long unidadesDevueltas, long unidadesNetas) {}
