package co.livorno.controlstock.reporte.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Movimientos del periodo (REQ-07, CU-03; ADR-011). movimientosDetalle es el número de movimientos del kardex que
 * cambian el físico en el rango (el detalle solo se exporta a Excel, máximo 50.000). Sin movimientos: 200 con
 * sinMovimientos = true y mensaje "No hay movimientos en el periodo" (null si hay); las filas pueden traer saldos sin
 * movimiento.
 */
public record MovimientosResponse(LocalDate desde, LocalDate hasta, Long categoriaId, String categoria,
        AgrupacionReporte agrupacion, boolean sinMovimientos, String mensaje, List<FilaMovimientos> filas,
        FilaMovimientos totales, long movimientosDetalle, List<String> notas, LocalDateTime generadoEn) {}
