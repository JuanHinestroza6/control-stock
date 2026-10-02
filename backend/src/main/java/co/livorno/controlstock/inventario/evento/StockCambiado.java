package co.livorno.controlstock.inventario.evento;

/** Se publica tras cada movimiento; el listener de alertas (AFTER_COMMIT) llega en la Fase 3. */
public record StockCambiado(Long varianteId) {
}
