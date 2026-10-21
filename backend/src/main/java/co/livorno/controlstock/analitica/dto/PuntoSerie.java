package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;

/** Mes de la serie: observada, disponibilidad (fracción 0-1 de días con stock), corregida y pronósticos del backtest. */
public record PuntoSerie(String periodo, int observada, BigDecimal disponibilidad, BigDecimal corregida,
        BigDecimal backtestSes, BigDecimal backtestHw) {}
