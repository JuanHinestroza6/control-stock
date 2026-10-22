package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ventas (facturado de ventas REGISTRADA) de un día; los días sin ventas llegan con 0. */
public record PuntoDia(LocalDate fecha, BigDecimal total, long numeroVentas) {}
