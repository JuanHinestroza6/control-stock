package co.livorno.controlstock.reporte.dto;

import java.time.LocalDate;

/** Filtro del reporte de movimientos del periodo; fechas y agrupación nulas toman los valores por defecto. */
public record FiltroMovimientos(LocalDate desde, LocalDate hasta, Long categoriaId, AgrupacionReporte agrupacion) {}
