package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;

/**
 * Insumos de lectura de una variante activa de un producto activo para la sugerencia de compra (ADR-010).
 * {@code demandaMedia} es la d̄ histórica de la última instantánea de la analítica (null si no hay).
 */
public record VarianteSugerible(Long id, Integer puntoReorden, int stockMinimo, int disponible, int leadTimeDias,
        int loteMinimo, BigDecimal demandaMedia) {
}
