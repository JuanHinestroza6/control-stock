package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;

/**
 * Un mes de la serie de tendencias con el mismo mes del año anterior. Variaciones en % (escala 1); null si el año
 * anterior es 0.
 */
public record PuntoTendencia(String periodo, long unidades, BigDecimal ingreso, long unidadesAnioAnterior,
        BigDecimal ingresoAnioAnterior, BigDecimal variacionUnidades, BigDecimal variacionIngreso) {}
