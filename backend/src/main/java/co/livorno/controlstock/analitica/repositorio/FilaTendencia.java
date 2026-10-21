package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Demanda comercial neta de un mes (unidades e ingreso), sin ventas anuladas. */
public record FilaTendencia(YearMonth mes, long unidades, BigDecimal ingreso) {}
