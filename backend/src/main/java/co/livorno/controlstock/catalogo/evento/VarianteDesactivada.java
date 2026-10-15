package co.livorno.controlstock.catalogo.evento;

/** Se publica al desactivar una variante (sola o con su producto); el listener de alertas (AFTER_COMMIT) cierra sus alertas. */
public record VarianteDesactivada(Long varianteId) {
}
