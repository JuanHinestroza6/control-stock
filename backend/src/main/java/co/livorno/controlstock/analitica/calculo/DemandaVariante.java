package co.livorno.controlstock.analitica.calculo;

import java.util.Objects;

/**
 * Insumo de la desagregación ({@link Desagregador}): una variante activa del producto con su demanda de inventario y sus
 * días con stock en la ventana de mezcla {@code [corte − 180, corte)}.
 *
 * @param varianteId   id de la variante (fija el orden de salida y desempata el reparto de centésimas)
 * @param demanda      Σ neto de la ventana (vendidas − devueltas aptas); un neto negativo se trata como 0
 * @param diasConStock días con stock y días existentes de la variante en la ventana
 *                     ({@link CorrectorCensura#diasConStock}); {@code existentes = 0} = variante sin historia
 */
public record DemandaVariante(long varianteId, long demanda, DiasConStock diasConStock) {

    public DemandaVariante {
        Objects.requireNonNull(diasConStock, "diasConStock");
    }
}
