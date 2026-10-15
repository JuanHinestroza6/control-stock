package co.livorno.controlstock.catalogo.repositorio;

/** ROP y clase ABC vigentes en una variante (cualquier estado), para que la analítica omita los UPDATE sin cambio. */
public record AnaliticaActualVariante(Long id, Integer puntoReorden, Character claseAbc) {
}
