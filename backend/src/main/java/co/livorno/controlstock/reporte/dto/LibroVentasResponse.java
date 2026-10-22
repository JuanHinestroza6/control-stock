package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Libro de ventas (REQ-07). lineasDetalle es el número de líneas de venta del rango (el detalle solo se exporta a
 * Excel, máximo 50.000). Sin ventas: 200 con sinVentas = true y mensaje "No hay ventas en el periodo" (null si hay).
 */
public record LibroVentasResponse(LocalDate desde, LocalDate hasta, PeriodoLibro agrupacion, boolean sinVentas,
        String mensaje, List<FilaLibro> filas, FilaLibro totales, List<VentaAnuladaItem> anuladas,
        long totalAnuladas, BigDecimal montoAnuladas, long lineasDetalle, List<String> notas,
        LocalDateTime generadoEn) {}
