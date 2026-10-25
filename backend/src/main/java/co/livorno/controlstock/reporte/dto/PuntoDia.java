package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ingreso neto de devoluciones de las ventas REGISTRADA de un día (por fecha de venta); los días sin ventas llegan con 0. */
public record PuntoDia(LocalDate fecha, BigDecimal total, long numeroVentas) {}
