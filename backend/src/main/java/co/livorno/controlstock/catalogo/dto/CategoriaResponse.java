package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Categoria;

public record CategoriaResponse(Long id, String nombre, long totalProductos) {

    public static CategoriaResponse de(Categoria c, long totalProductos) {
        return new CategoriaResponse(c.getId(), c.getNombre(), totalProductos);
    }
}
