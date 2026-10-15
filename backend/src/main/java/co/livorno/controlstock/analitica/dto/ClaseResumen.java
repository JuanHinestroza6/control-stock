package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;

/** Totales de una clase ABC. {@code participacion} es el porcentaje del ingreso (0–100, escala 2). */
public record ClaseResumen(String clase, long variantes, BigDecimal ingreso, BigDecimal participacion) {
}
