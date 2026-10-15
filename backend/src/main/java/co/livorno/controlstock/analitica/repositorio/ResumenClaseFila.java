package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;

/** Variantes e ingreso neto de una clase ABC en la instantánea (variantes activas de productos activos). */
public record ResumenClaseFila(String clase, long variantes, BigDecimal ingreso) {
}
