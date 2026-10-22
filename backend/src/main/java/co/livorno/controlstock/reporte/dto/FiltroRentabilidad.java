package co.livorno.controlstock.reporte.dto;

import java.time.LocalDate;

/** Filtro ya validado de rentabilidad: rango inclusivo por fecha de venta, categoría opcional (ADR-011). */
public record FiltroRentabilidad(LocalDate desde, LocalDate hasta, Long categoriaId, AgrupacionReporte agrupacion,
        CriterioRentabilidad orden) {}
