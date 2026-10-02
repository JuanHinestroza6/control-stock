package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Categoria;

public record CategoriaRef(Long id, String nombre) {

    public static CategoriaRef de(Categoria c) {
        return new CategoriaRef(c.getId(), c.getNombre());
    }
}
