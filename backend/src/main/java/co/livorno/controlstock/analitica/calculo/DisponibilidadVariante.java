package co.livorno.controlstock.analitica.calculo;

import java.util.List;
import java.util.Objects;

/**
 * Insumo de la disponibilidad ponderada de un producto: una variante activa con su demanda observada en toda la serie
 * (define su peso) y sus días con stock por mes, alineados con la {@link SerieMensual}.
 *
 * @param demandaObservada Σ neto de la variante en los meses de la serie; un neto negativo (devoluciones de ventas
 *                         anteriores a la serie) se trata como 0
 * @param porMes           un elemento por mes de la serie, en el mismo orden
 */
public record DisponibilidadVariante(long demandaObservada, List<DiasConStock> porMes) {

    public DisponibilidadVariante {
        Objects.requireNonNull(porMes, "porMes");
        porMes = List.copyOf(porMes);
    }
}
