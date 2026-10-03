package co.livorno.controlstock.inventario.repositorio;

/** Apartados ACTIVOS agregados por variante (proyección de ReservaRepository). */
public record ApartadosVariante(Long varianteId, String sku, long apartados, long unidades) {
}
