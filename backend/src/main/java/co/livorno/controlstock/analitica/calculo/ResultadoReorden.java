package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.math.BigDecimal;

/**
 * Punto de reorden de una variante con los insumos que lo explican (columnas de {@code analitica_variante}).
 *
 * @param puntoReorden   null solo con {@link MetodoReorden#SIN_HISTORIA} (el umbral sigue siendo stockMinimo)
 * @param diasHistoria   n de la serie
 * @param demandaMedia   d̄, escala 4 HALF_UP
 * @param desviacion     s muestral, escala 4 HALF_UP
 * @param nivelServicio  p de la clase usada (clase null → C)
 * @param z              z de la clase usada
 * @param stockSeguridad escala 2 HALF_UP: NORMAL z·s·√L; POISSON ROP − μL (informativo, puede ser negativo);
 *                       SIN_DEMANDA 0; SIN_HISTORIA null
 */
public record ResultadoReorden(Integer puntoReorden, MetodoReorden metodo, int diasHistoria, BigDecimal demandaMedia,
        BigDecimal desviacion, int leadTimeDias, BigDecimal nivelServicio, BigDecimal z, BigDecimal stockSeguridad) {
}
