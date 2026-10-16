package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Pronóstico de una variante desagregado desde el de su producto ({@link Desagregador}).
 *
 * @param varianteId    id de la variante
 * @param participacion part_v normalizada, escala 6 HALF_UP (0 si la variante no tiene demanda en la ventana)
 * @param pronostico    un valor por mes del pronóstico del producto, escala 2; por mes, la suma de las variantes es
 *                      exactamente la del producto (salvo que todas tengan participación 0)
 */
public record PronosticoVariante(long varianteId, BigDecimal participacion, List<BigDecimal> pronostico) {

    public PronosticoVariante {
        Objects.requireNonNull(participacion, "participacion");
        pronostico = List.copyOf(pronostico);
    }
}
