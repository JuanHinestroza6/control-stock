package co.livorno.controlstock.catalogo.evento;

/** Se publica al editar el stock mínimo de una variante; el listener de alertas (AFTER_COMMIT) reevalúa. */
public record UmbralCambiado(Long varianteId) {
}
