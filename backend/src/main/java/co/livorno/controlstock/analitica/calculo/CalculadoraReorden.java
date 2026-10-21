package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import co.livorno.controlstock.analitica.config.PropiedadesAnalitica.ParametrosClase;
import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 *
 * <p>Desde la fase 6 (ADR-010, decisión 3) hay una sobrecarga con el pronóstico de la variante:
 * <pre>
 * μL = Σ_{i=0..L−1} tasa(corte + i)      tasa = pronóstico del mes del día / días del mes (horizonte agotado → último)
 * s  = desviación de la serie diaria observada de 90 días (la misma de la fase 5)
 * n &lt; historiaMinima → SIN_HISTORIA ; μL = 0 → SIN_DEMANDA ; μL ≥ 5 → NORMAL ceil(μL + z·s·√L) ; μL &lt; 5 → POISSON
 * Sin pronóstico (null) → fórmula de la fase 5, fuente HISTORICA.
 * </pre>
 * Ejemplo: octubre 31 (1/día), noviembre 60 (2/día); corte 25-oct, L = 15 → μL = 7 × 1 + 8 × 2 = 23; s = 1,2, clase A →
 * SS = 1,65 × 1,2 × √15 = 7,67 → ROP = ceil(30,67) = 31.
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
        validar(serie, leadTimeDias);
        return decidir(muHistorica(serie, leadTimeDias), serie.total() == 0, serie, leadTimeDias, clase);
    }

    /**
     * ROP con la fuente de la demanda (ADR-010, decisión 3). Con {@code pronostico} null usa la fórmula de la fase 5 y
     * devuelve el mismo {@link ResultadoReorden} que {@link #calcular(SerieDemanda, int, Character)}.
     *
     * @param serie       serie diaria observada de 90 días (n y s salen siempre de ella)
     * @param pronostico  pronóstico mensual de la variante (desagregado), o null si el producto no tiene pronóstico
     * @param corte       primer día del lead time (hoy; la ventana observada termina el día anterior)
     * @throws IllegalArgumentException si el lead time es negativo
     */
    public ReordenConFuente calcular(SerieDemanda serie, int leadTimeDias, Character clase,
            PronosticoMensual pronostico, LocalDate corte) {
        if (pronostico == null) {
            ResultadoReorden r = calcular(serie, leadTimeDias, clase);
            BigDecimal mu = r.metodo() == MetodoReorden.SIN_HISTORIA ? null
                    : escala(muHistorica(serie, leadTimeDias), 2);
            return new ReordenConFuente(r, FuenteDemanda.HISTORICA, mu, null);
        }
        validar(serie, leadTimeDias);
        Objects.requireNonNull(corte, "corte");
        double mu = pronostico.demanda(corte, leadTimeDias);
        double tasa = leadTimeDias > 0 ? mu / leadTimeDias : pronostico.tasaDiaria(corte);
        // Sin demanda: el pronóstico del lead time es 0 (con L = 0, la tasa del día del corte).
        boolean sinDemanda = (leadTimeDias > 0 ? mu : tasa) <= TOLERANCIA;
        ResultadoReorden r = decidir(sinDemanda ? 0.0 : mu, sinDemanda, serie, leadTimeDias, clase);
        return new ReordenConFuente(r, FuenteDemanda.PRONOSTICO, escala(mu, 2), escala(tasa, 4));
    }

    private static void validar(SerieDemanda serie, int leadTimeDias) {
        Objects.requireNonNull(serie, "serie");
        if (leadTimeDias < 0) {
            throw new IllegalArgumentException("El lead time no puede ser negativo: " + leadTimeDias);
        }
    }

    /** μL histórica = Σ serie × L / n (0 sin historia). */
    private static double muHistorica(SerieDemanda serie, int leadTimeDias) {
        return serie.dias() == 0 ? 0.0 : (double) serie.total() * leadTimeDias / serie.dias();
    }

    /** Reglas comunes a ambas fuentes, dados μL y si la fuente no tiene demanda. */
    private ResultadoReorden decidir(double mu, boolean sinDemanda, SerieDemanda serie, int leadTimeDias,
            Character clase) {
        ParametrosClase parametros = propiedades.parametrosDe(clase);
        int n = serie.dias();
        BigDecimal media = escala(serie.media(), 4);
        BigDecimal desviacion = escala(serie.desviacion(), 4);

        if (n < propiedades.historiaMinimaDias()) {
            return resultado(null, MetodoReorden.SIN_HISTORIA, serie, media, desviacion, leadTimeDias, parametros,
                    null);
        }
        if (sinDemanda) {
            return resultado(0, MetodoReorden.SIN_DEMANDA, serie, media, desviacion, leadTimeDias, parametros,
                    BigDecimal.ZERO.setScale(2));
        }
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
