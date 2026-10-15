package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen de la última clasificación ABC. Sin ejecución EXITOSA: {@code ejecucion} y las fechas son null, el ingreso
 * es 0 y las tres clases vienen en cero.
 */
public record ResumenAbcResponse(
        EjecucionAnaliticaResponse ejecucion,
        LocalDate ventanaDesde,
        LocalDate ventanaHasta,
        BigDecimal totalIngreso,
        List<ClaseResumen> clases) {
}
