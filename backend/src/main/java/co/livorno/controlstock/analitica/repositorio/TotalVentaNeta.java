package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;

/** Unidades e ingreso netos de un periodo (y categoría opcional). */
public record TotalVentaNeta(long unidades, BigDecimal ingreso) {}
