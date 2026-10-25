package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ventas de hoy y del mes en curso (ingreso neto de devoluciones por fecha de venta). margenMes y margenMesPorcentaje
 * solo llegan a ADMIN y CONTABILIDAD (null para VENDEDOR). ultimos7Dias: 7 puntos ascendentes terminando hoy.
 */
public record ResumenVentas(BigDecimal ventasHoy, long numeroVentasHoy, BigDecimal ventasMes, long numeroVentasMes,
        BigDecimal margenMes, BigDecimal margenMesPorcentaje, List<PuntoDia> ultimos7Dias) {}
