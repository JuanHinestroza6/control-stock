package co.livorno.controlstock.catalogo.repositorio;

/** Totales de las variantes activas de un producto. */
public record TotalesProducto(Long productoId, Long totalVariantes, Long stockDisponible) {
}
