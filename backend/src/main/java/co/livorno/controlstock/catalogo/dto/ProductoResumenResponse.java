package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Producto;
import java.math.BigDecimal;

public record ProductoResumenResponse(Long id, String codigo, String nombre, CategoriaRef categoria,
        BigDecimal precioVenta, boolean activo, long totalVariantes, long stockDisponibleTotal) {

    public static ProductoResumenResponse de(Producto p, long totalVariantes, long stockDisponibleTotal) {
        return new ProductoResumenResponse(p.getId(), p.getCodigo(), p.getNombre(), CategoriaRef.de(p.getCategoria()),
                p.getPrecioVenta(), p.isActivo(), totalVariantes, stockDisponibleTotal);
    }
}
