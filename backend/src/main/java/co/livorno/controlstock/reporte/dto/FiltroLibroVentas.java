package co.livorno.controlstock.reporte.dto;

import java.time.LocalDate;

/** Filtro ya validado del libro de ventas: rango inclusivo y agrupación por día o mes. */
public record FiltroLibroVentas(LocalDate desde, LocalDate hasta, PeriodoLibro agrupacion) {}
