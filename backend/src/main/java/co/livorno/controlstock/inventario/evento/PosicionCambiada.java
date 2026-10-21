package co.livorno.controlstock.inventario.evento;

/**
 * Se publica por línea al aprobar un plan de compras o al cancelar uno APROBADO: cambia lo que viene en camino sin
 * mover stock (ADR-010). El listener de alertas lo re-evalúa AFTER_COMMIT (B8).
 */
public record PosicionCambiada(Long varianteId) {
}
