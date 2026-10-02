package co.livorno.controlstock.catalogo.repositorio;

/** Total de productos de una categoría (resultado de la consulta agregada). */
public record ConteoPorCategoria(Long categoriaId, long total) {}
