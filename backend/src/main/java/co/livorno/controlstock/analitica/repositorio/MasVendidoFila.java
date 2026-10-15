package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;

/** Fila cruda del ranking. Los campos de variante son null al agrupar por producto. */
public record MasVendidoFila(long productoId, String productoCodigo, String productoNombre, long categoriaId,
        String categoriaNombre, Long varianteId, String sku, String talla, String color, long unidades,
        BigDecimal ingreso, boolean activo) {}
