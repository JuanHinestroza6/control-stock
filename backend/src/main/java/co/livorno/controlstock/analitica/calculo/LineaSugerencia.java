package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Línea sugerida de un plan de compras ({@link CalculadoraSugerenciaCompra}).
 *
 * @param varianteId     id de la variante
 * @param umbral         ROP, o stockMinimo si no hay ROP
 * @param posicion       disponible + en camino
 * @param fuente         PRONOSTICO (tasa pronosticada) o HISTORICA (d̄ observada × R)
 * @param demandaPeriodo demanda de los R días que siguen al lead time, escala 2 HALF_UP
 * @param nivelObjetivo  S, escala 2 HALF_UP
 * @param faltante       ceil(S − posición), antes del lote (&gt; 0)
 * @param cantidad       faltante redondeado hacia arriba al múltiplo de loteMinimo (&gt; 0): cantidadSugerida
 * @param justificacion  texto legible del cálculo (cabe en {@code detalle_plan.justificacion}, 255)
 */
public record LineaSugerencia(long varianteId, int umbral, long posicion, FuenteDemanda fuente,
        BigDecimal demandaPeriodo, BigDecimal nivelObjetivo, int faltante, int cantidad, String justificacion) {

    public LineaSugerencia {
        Objects.requireNonNull(fuente, "fuente");
        Objects.requireNonNull(demandaPeriodo, "demandaPeriodo");
        Objects.requireNonNull(nivelObjetivo, "nivelObjetivo");
        Objects.requireNonNull(justificacion, "justificacion");
    }
}
