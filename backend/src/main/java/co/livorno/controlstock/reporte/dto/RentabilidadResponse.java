package co.livorno.controlstock.reporte.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte de rentabilidad (REQ-03). Sin ventas en el periodo: 200 con sinVentas = true, filas vacías y mensaje "No hay
 * ventas en el periodo" (null si hay ventas). Los filtros son los efectivos tras aplicar valores por defecto. Sin datos
 * de clientes (Ley 1581).
 */
public record RentabilidadResponse(LocalDate desde, LocalDate hasta, Long categoriaId, String categoria,
        AgrupacionReporte agrupacion, CriterioRentabilidad orden, boolean sinVentas, String mensaje,
        List<FilaRentabilidad> filas, TotalesRentabilidad totales, List<String> notas, LocalDateTime generadoEn) {}
