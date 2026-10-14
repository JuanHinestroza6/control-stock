package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;

/**
 * Resultado del ABC para una variante.
 *
 * @param ingresoNeto    ingreso de la entrada, escala 2
 * @param posicion       1 = mayor ingreso (desempate por id de variante)
 * @param participacion  ingreso / total, escala 6 HALF_UP (0 si el total es 0)
 * @param acumulado      Σ ingreso hasta esta posición / total, escala 6 HALF_UP (0 si el total es 0)
 * @param clase          'A', 'B' o 'C'
 */
public record ClasificacionAbc(long varianteId, BigDecimal ingresoNeto, int posicion, BigDecimal participacion,
        BigDecimal acumulado, char clase) {
}
