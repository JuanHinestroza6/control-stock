package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila del libro de ventas. periodo es yyyy-MM-dd (DIA) o yyyy-MM (MES); null en la fila de totales. Las ventas cuentan
 * en la fecha de la venta y las devoluciones (y el reverso de su costo) en la fecha de la devolución, por eso
 * ingresoNeto, costoVentas y margenBruto pueden ser negativos.
 */
public record FilaLibro(String periodo, long numeroVentas, BigDecimal ventasLista, BigDecimal descuentos,
        BigDecimal facturado, BigDecimal devoluciones, BigDecimal ingresoNeto, BigDecimal costoVentas,
        BigDecimal margenBruto, BigDecimal mermaNoApta, long unidadesVendidas, long unidadesDevueltas) {}
