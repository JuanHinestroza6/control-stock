package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.util.Objects;

/**
 * Resultado de {@link HoltWintersMultiplicativo#ajustar(double[])}: o bien el mejor ajuste de la rejilla, o bien el
 * motivo por el que Holt-Winters no compite (exactamente uno de los dos es distinto de null).
 */
public record ResultadoHoltWinters(AjusteHoltWinters ajuste, MotivoSinHoltWinters motivo) {

    public ResultadoHoltWinters {
        if ((ajuste == null) == (motivo == null)) {
            throw new IllegalArgumentException("Debe haber un ajuste o un motivo, no ambos ni ninguno.");
        }
    }

    public static ResultadoHoltWinters ajustado(AjusteHoltWinters ajuste) {
        return new ResultadoHoltWinters(Objects.requireNonNull(ajuste, "ajuste"), null);
    }

    public static ResultadoHoltWinters noElegible(MotivoSinHoltWinters motivo) {
        return new ResultadoHoltWinters(null, Objects.requireNonNull(motivo, "motivo"));
    }

    public boolean elegible() {
        return ajuste != null;
    }
}
