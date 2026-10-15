package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import co.livorno.controlstock.analitica.config.PropiedadesAnalitica.ParametrosClase;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Punto de reorden híbrido sin piso (ADR-009), cálculo puro.
 *
 * <pre>
 * Parámetros por clase (PropiedadesAnalitica.parametrosDe): A z=1,65 p=0,95 | B z=1,28 p=0,90 | C z=0,84 p=0,80 ;
 * clase null → C.  L = leadTimeDias del producto.
 * n &lt; historiaMinima        → ROP = null                         SIN_HISTORIA (umbral = stockMinimo)
 * d̄ = 0                     → ROP = 0                            SIN_DEMANDA
 * μL = d̄ × L ≥ umbral (5)   → ROP = ceil(μL + z × s × √L)        NORMAL
 * μL &lt; umbral              → ROP = mín r con P(Poisson(μL) ≤ r) ≥ p   POISSON
 * </pre>
 *
 * Sin piso de stockMinimo (D2) y sin loteMinimo. μL se calcula como Σ serie × L / n para no acumular error de
 * redondeo (30 u en 45 días con L = 15 da exactamente 5). La frontera μL ≥ umbral y el {@code ceil} toleran 1e-9:
 * un valor que en aritmética exacta es entero (por ejemplo μL = 30 con s = 0) no sube una unidad por el redondeo.
 */
public final class CalculadoraReorden {

    static final double TOLERANCIA = 1e-9;

    private final PropiedadesAnalitica propiedades;
    private final double umbralPoisson;

    public CalculadoraReorden(PropiedadesAnalitica propiedades) {
        this.propiedades = Objects.requireNonNull(propiedades, "propiedades");
        this.umbralPoisson = propiedades.umbralPoisson().doubleValue();
    }

    /**
     * @param clase 'A', 'B', 'C' o null (sin clase → parámetros de C)
     * @throws IllegalArgumentException si el lead time es negativo
     */
    public ResultadoReorden calcular(SerieDemanda serie, int leadTimeDias, Character clase) {
        Objects.requireNonNull(serie, "serie");
        if (leadTimeDias < 0) {
            throw new IllegalArgumentException("El lead time no puede ser negativo: " + leadTimeDias);
        }
        ParametrosClase parametros = propiedades.parametrosDe(clase);
        int n = serie.dias();
        BigDecimal media = escala(serie.media(), 4);
        BigDecimal desviacion = escala(serie.desviacion(), 4);

        if (n < propiedades.historiaMinimaDias()) {
            return resultado(null, MetodoReorden.SIN_HISTORIA, serie, media, desviacion, leadTimeDias, parametros,
                    null);
        }
        if (serie.total() == 0) {
            return resultado(0, MetodoReorden.SIN_DEMANDA, serie, media, desviacion, leadTimeDias, parametros,
                    BigDecimal.ZERO.setScale(2));
        }

        double mu = (double) serie.total() * leadTimeDias / n;
        if (mu >= umbralPoisson - TOLERANCIA) {
            double ss = parametros.z().doubleValue() * serie.desviacion() * Math.sqrt(leadTimeDias);
            int rop = (int) Math.ceil(mu + ss - TOLERANCIA);
            return resultado(rop, MetodoReorden.NORMAL, serie, media, desviacion, leadTimeDias, parametros,
                    escala(ss, 2));
        }
        int rop = DistribucionPoisson.cuantil(mu, parametros.nivelServicio().doubleValue());
        return resultado(rop, MetodoReorden.POISSON, serie, media, desviacion, leadTimeDias, parametros,
                escala(rop - mu, 2));
    }

    private static ResultadoReorden resultado(Integer rop, MetodoReorden metodo, SerieDemanda serie,
            BigDecimal media, BigDecimal desviacion, int leadTimeDias, ParametrosClase parametros,
            BigDecimal stockSeguridad) {
        return new ResultadoReorden(rop, metodo, serie.dias(), media, desviacion, leadTimeDias,
                parametros.nivelServicio(), parametros.z(), stockSeguridad);
    }

    private static BigDecimal escala(double valor, int escala) {
        return BigDecimal.valueOf(valor).setScale(escala, RoundingMode.HALF_UP);
    }
}
