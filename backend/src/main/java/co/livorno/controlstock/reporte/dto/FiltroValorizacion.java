package co.livorno.controlstock.reporte.dto;

/** Filtro ya validado de valorización (al momento de la consulta); por defecto incluye inactivas con stock. */
public record FiltroValorizacion(Long categoriaId, AgrupacionReporte agrupacion, boolean incluirInactivas) {}
