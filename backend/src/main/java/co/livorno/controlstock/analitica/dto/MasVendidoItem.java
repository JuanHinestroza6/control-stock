package co.livorno.controlstock.analitica.dto;

import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.Talla;
import java.math.BigDecimal;

/** Fila del ranking. Los campos de variante son null cuando la agrupación es PRODUCTO. Sin costos ni margen. */
public record MasVendidoItem(int posicion, Long productoId, String productoCodigo, String productoNombre,
        CategoriaRef categoria, Long varianteId, String sku, Talla talla, String color, long unidades,
        BigDecimal ingreso, BigDecimal participacion, boolean activo) {}
