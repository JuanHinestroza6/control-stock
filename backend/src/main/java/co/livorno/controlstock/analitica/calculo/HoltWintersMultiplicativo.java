package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.util.Optional;

/**
 * Holt-Winters multiplicativo con estacionalidad anual (m = 12) para la serie mensual corregida de un producto
 * (ADR-010, decisión 2), funciones puras.
 *
 * <pre>
 * Elegibilidad (si falla, HW no compite y el producto usa SES):
 *   n &lt; 24                                        → POCA_HISTORIA  (dos temporadas para inicializar)
 *   algún y_t = 0                                 → MES_EN_CERO
 *   nivel_0 + i·tend_0 ≤ 0 para algún i = 1..12   → NIVEL_NO_POSITIVO
 *   todas las combinaciones llevan el nivel a ≤ 0 → NIVEL_NO_POSITIVO
 *
 * Inicialización con los dos primeros años; índices como RAZONES respecto de la tendencia, normalizados a media 1:
 *   ȳ1, ȳ2 = promedios de y_1..y_12 y de y_13..y_24
 *   tend_0  = (ȳ2 − ȳ1) / 12 ; nivel_0 = ȳ1 − 6,5·tend_0      (la recta pasa por ȳ1 a mitad del año 1, i = 6,5)
 *   r_i     = y_i / (nivel_0 + i·tend_0), i = 1..12 ; s_i = r_i × 12 / Σ r_j
 *
 * Para t = 1..n (s_{t−12} = s_i inicial cuando t ≤ 12):
 *   ŷ_t     = (nivel_{t−1} + tend_{t−1}) × s_{t−12}            (error de un paso, entra en el SSE)
 *   nivel_t = α(y_t / s_{t−12}) + (1 − α)(nivel_{t−1} + tend_{t−1})
 *   tend_t  = β(nivel_t − nivel_{t−1}) + (1 − β)·tend_{t−1}
 *   s_t     = γ(y_t / nivel_t) + (1 − γ)·s_{t−12}
 * pronóstico_{n+h} = max(0, (nivel_n + h·tend_n) × s_{n−12+((h−1) mod 12)+1})
 *
 * (α, β, γ) ∈ {0,1; 0,3; 0,5; 0,7; 0,9}³ por menor SSE = Σ_{t=1..n} (y_t − ŷ_t)²; empate → orden lexicográfico
 * ascendente (se recorren α, β, γ en orden y solo reemplaza un SSE estrictamente menor). Una combinación cuyo nivel_t
 * llega a ≤ 0 durante el ajuste se descarta.
 * </pre>
 *
 * Empate: dos SSE se consideran iguales si difieren en menos de {@code 1e-9 × max(1, SSE)} (ruido de punto flotante;
 * una serie sin error da SSE ≈ 0 con todas las combinaciones y se elige (0,1; 0,1; 0,1)).
 *
 * Con y_t &gt; 0 y nivel_t &gt; 0, los índices s_t quedan siempre &gt; 0 (combinación convexa de valores positivos), así
 * que y / s nunca divide por 0.
 */
public final class HoltWintersMultiplicativo {

    /** Periodo estacional: 12 meses. */
    public static final int PERIODO = 12;

    /** Dos temporadas completas para inicializar. */
    public static final int MESES_MINIMOS = 2 * PERIODO;

    /** Rejilla de cada parámetro (α, β y γ). */
    static final double[] REJILLA = {0.1, 0.3, 0.5, 0.7, 0.9};

    private HoltWintersMultiplicativo() {
    }

    /**
     * Motivo por el que la serie no admite Holt-Winters antes de recorrer la rejilla (vacío si es elegible). Orden de
     * evaluación: POCA_HISTORIA, MES_EN_CERO, NIVEL_NO_POSITIVO (inicial).
     *
     * @throws IllegalArgumentException si la serie está vacía o trae un valor negativo o no finito
     */
    public static Optional<MotivoSinHoltWinters> motivoSinHoltWinters(double[] serie) {
        SuavizadoExponencial.validar(serie);
        if (serie.length < MESES_MINIMOS) {
            return Optional.of(MotivoSinHoltWinters.POCA_HISTORIA);
        }
        for (double y : serie) {
            if (y == 0) {
                return Optional.of(MotivoSinHoltWinters.MES_EN_CERO);
            }
        }
        double[] recta = rectaPrimerAnio(serie);
        if (!nivelInicialPositivo(recta)) {
            return Optional.of(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);
        }
        return Optional.empty();
    }

    /**
     * Inicialización con los dos primeros años (nivel_0, tend_0, razones e índices de media 1).
     *
     * @throws IllegalArgumentException si la serie no es elegible (ver {@link #motivoSinHoltWinters}); el mensaje
     *                                  incluye el motivo
     */
    public static InicializacionHoltWinters inicializar(double[] serie) {
        exigirElegible(serie);
        return inicializarSinValidar(serie);
    }

    /**
     * Ajusta (α, β, γ) por rejilla: menor SSE; empate → orden lexicográfico ascendente; combinaciones con nivel ≤ 0
     * descartadas.
     *
     * @return el mejor ajuste, o el motivo por el que HW no compite (incluido NIVEL_NO_POSITIVO si se descartan las 125
     *         combinaciones)
     * @throws IllegalArgumentException si la serie está vacía o trae un valor negativo o no finito
     */
    public static ResultadoHoltWinters ajustar(double[] serie) {
        Optional<MotivoSinHoltWinters> motivo = motivoSinHoltWinters(serie);
        if (motivo.isPresent()) {
            return ResultadoHoltWinters.noElegible(motivo.get());
        }
        InicializacionHoltWinters inicio = inicializarSinValidar(serie);
        AjusteHoltWinters mejor = null;
        for (double alfa : REJILLA) {
            for (double beta : REJILLA) {
                for (double gamma : REJILLA) {
                    Optional<AjusteHoltWinters> ajuste = ajustarSinValidar(serie, inicio, alfa, beta, gamma);
                    if (ajuste.isPresent()
                            && (mejor == null || SuavizadoExponencial.esMenor(ajuste.get().sse(), mejor.sse()))) {
                        mejor = ajuste.get();
                    }
                }
            }
        }
        return mejor == null
                ? ResultadoHoltWinters.noElegible(MotivoSinHoltWinters.NIVEL_NO_POSITIVO)
                : ResultadoHoltWinters.ajustado(mejor);
    }

    /**
     * Ajuste con (α, β, γ) fijos.
     *
     * @return vacío si el nivel llega a ≤ 0 en algún t (combinación descartada)
     * @throws IllegalArgumentException si un parámetro no está en (0, 1] o la serie no es elegible
     */
    public static Optional<AjusteHoltWinters> ajustar(double[] serie, double alfa, double beta, double gamma) {
        validarParametro("α", alfa);
        validarParametro("β", beta);
        validarParametro("γ", gamma);
        exigirElegible(serie);
        return ajustarSinValidar(serie, inicializarSinValidar(serie), alfa, beta, gamma);
    }

    private static Optional<AjusteHoltWinters> ajustarSinValidar(double[] y, InicializacionHoltWinters inicio,
            double alfa, double beta, double gamma) {
        int n = y.length;
        // s[t] para t = 0..n: s[0..11] son los índices iniciales (s_{t−12} de t = 1..12); s[t + 11] = s_t.
        double[] s = new double[n + PERIODO];
        System.arraycopy(inicio.indices(), 0, s, 0, PERIODO);
        double nivel = inicio.nivel();
        double tendencia = inicio.tendencia();
        double sse = 0;
        for (int t = 1; t <= n; t++) {
            double yt = y[t - 1];
            double estacionAnterior = s[t - 1];                 // s_{t−12}
            double base = nivel + tendencia;
            double error = yt - base * estacionAnterior;          // y_t − ŷ_t
            sse += error * error;
            double nuevoNivel = alfa * (yt / estacionAnterior) + (1 - alfa) * base;
            if (!(nuevoNivel > 0)) {
                return Optional.empty();
            }
            tendencia = beta * (nuevoNivel - nivel) + (1 - beta) * tendencia;
            nivel = nuevoNivel;
            s[t - 1 + PERIODO] = gamma * (yt / nivel) + (1 - gamma) * estacionAnterior;   // s_t
        }
        double[] ultimos = new double[PERIODO];
        System.arraycopy(s, n, ultimos, 0, PERIODO);              // s_{n−11} … s_n
        return Optional.of(new AjusteHoltWinters(alfa, beta, gamma, nivel, tendencia, ultimos, sse));
    }

    private static InicializacionHoltWinters inicializarSinValidar(double[] y) {
        double[] recta = rectaPrimerAnio(y);
        double nivel0 = recta[0];
        double tend0 = recta[1];
        double[] razones = new double[PERIODO];
        double suma = 0;
        for (int i = 1; i <= PERIODO; i++) {
            razones[i - 1] = y[i - 1] / (nivel0 + i * tend0);
            suma += razones[i - 1];
        }
        double[] indices = new double[PERIODO];
        for (int i = 0; i < PERIODO; i++) {
            indices[i] = razones[i] * PERIODO / suma;
        }
        return new InicializacionHoltWinters(nivel0, tend0, razones, indices);
    }

    /** {nivel_0, tend_0} a partir de los promedios de los dos primeros años. */
    private static double[] rectaPrimerAnio(double[] y) {
        double suma1 = 0;
        double suma2 = 0;
        for (int i = 0; i < PERIODO; i++) {
            suma1 += y[i];
            suma2 += y[i + PERIODO];
        }
        double promedio1 = suma1 / PERIODO;
        double promedio2 = suma2 / PERIODO;
        double tend0 = (promedio2 - promedio1) / PERIODO;
        double nivel0 = promedio1 - 6.5 * tend0;
        return new double[] {nivel0, tend0};
    }

    private static boolean nivelInicialPositivo(double[] recta) {
        for (int i = 1; i <= PERIODO; i++) {
            if (!(recta[0] + i * recta[1] > 0)) {
                return false;
            }
        }
        return true;
    }

    private static void exigirElegible(double[] serie) {
        Optional<MotivoSinHoltWinters> motivo = motivoSinHoltWinters(serie);
        if (motivo.isPresent()) {
            throw new IllegalArgumentException("La serie no admite Holt-Winters multiplicativo: " + motivo.get() + ".");
        }
    }

    private static void validarParametro(String nombre, double valor) {
        if (!(valor > 0 && valor <= 1)) {
            throw new IllegalArgumentException(nombre + " debe estar en (0, 1]: " + valor);
        }
    }
}
