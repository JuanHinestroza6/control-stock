package co.livorno.controlstock.comun.exportacion;

/** Par etiqueta-valor que describe un filtro aplicado al reporte (p. ej. "Desde" = "2026-01-01"). */
public record FiltroReporte(String etiqueta, String valor) {}
