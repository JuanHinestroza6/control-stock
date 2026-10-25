package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila del reporte de movimientos: estado del físico de un grupo (categoría, producto o variante) en el periodo. Se
 * cumple {@code saldoFinal = saldoInicial + entradas − ventas + anulaciones + devoluciones + ajustesPositivos −
 * ajustesNegativos}. valorEntradas es Σ cantidad × costo unitario de las entradas. id, codigo y categoria son null en
 * la fila de totales; codigo y categoria también con agrupación CATEGORIA.
 */
public record FilaMovimientos(Long id, String codigo, String nombre, String categoria, long saldoInicial,
        long entradas, BigDecimal valorEntradas, long ventas, long anulaciones, long devoluciones,
        long ajustesPositivos, long ajustesNegativos, long saldoFinal) {}
