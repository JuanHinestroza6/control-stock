package co.livorno.controlstock.reporte.dto;

/** Apartados ACTIVOS (solo conteos, sin datos del cliente). porVencer24h: vencen dentro de 24 h. */
public record ResumenReservas(long activas, long unidadesReservadas, long porVencer24h) {}
