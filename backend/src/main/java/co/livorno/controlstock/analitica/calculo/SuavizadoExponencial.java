package co.livorno.controlstock.analitica.calculo;

import java.util.Objects;

/**
 * Suavizado exponencial simple (SES) para la serie mensual corregida de un producto (ADR-010, decisión 2), funciones
 * puras.
 *
 * <pre>
 * nivel_1 = y_1
 * nivel_t = α·y_t + (1 − α)·nivel_{t−1}
 * pronóstico_{n+h} = max(0, nivel_n)                         (constante en el horizonte)
 * α ∈ {0,1; 0,2; … ; 0,9}: menor SSE = Σ_{t=2..n} (y_t − nivel_{t−1})² ; empate → menor α
 * </pre>
 *
 * Empate: dos SSE se consideran iguales si difieren en menos de {@code 1e-9 × max(1, SSE)} (ruido de punto flotante;
 * por ejemplo, una serie constante da SSE 0 con todos los α y se elige 0,1). Con n = 1 el SSE es 0 y gana α = 0,1.
 *
 * Ejemplo a mano: α = 0,5 sobre 10, 12, 14 → niveles 10, 11, 12,5 → pronóstico 12,5 ;
 * SSE = (12 − 10)² + (14 − 11)² = 4 + 9 = 13.
 */
public final class SuavizadoExponencial {

    /** Rejilla de α: 0,1 … 0,9 con paso 0,1 (k / 10 para no acumular error de punto flotante). */
    static final double[] REJILLA_ALFA = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};

    static final double TOLERANCIA_EMPATE = 1e-9;

    private SuavizadoExponencial() {
    }

    /**
     * Ajusta α por rejilla (menor SSE; empate → menor α).
     *
     * @param serie y_1..y_n, n ≥ 1, valores finitos y ≥ 0 (la serie corregida ya viene recortada a 0)
     * @throws IllegalArgumentException si la serie está vacía o trae un valor negativo o no finito
     */
    public static AjusteSes ajustar(double[] serie) {
        validar(serie);
        AjusteSes mejor = null;
        for (double alfa : REJILLA_ALFA) {
            AjusteSes ajuste = ajustarSinValidar(serie, alfa);
            if (mejor == null || esMenor(ajuste.sse(), mejor.sse())) {
                mejor = ajuste;
            }
        }
        return mejor;
    }

    /**
     * Ajuste con un α fijo.
     *
     * @throws IllegalArgumentException si α no está en (0, 1] o la serie no es válida
     */
    public static AjusteSes ajustar(double[] serie, double alfa) {
        validar(serie);
        if (!(alfa > 0 && alfa <= 1)) {
            throw new IllegalArgumentException("α debe estar en (0, 1]: " + alfa);
        }
        return ajustarSinValidar(serie, alfa);
    }

    private static AjusteSes ajustarSinValidar(double[] y, double alfa) {
        double nivel = y[0];
        double sse = 0;
        for (int t = 1; t < y.length; t++) {
            double error = y[t] - nivel;
            sse += error * error;
            nivel = alfa * y[t] + (1 - alfa) * nivel;
        }
        return new AjusteSes(alfa, nivel, sse);
    }

    /** true si {@code sse} es estrictamente menor que {@code mejor} fuera de la tolerancia de empate. */
    static boolean esMenor(double sse, double mejor) {
        return sse < mejor - TOLERANCIA_EMPATE * Math.max(1.0, mejor);
    }

    static void validar(double[] serie) {
        Objects.requireNonNull(serie, "serie");
        if (serie.length == 0) {
            throw new IllegalArgumentException("La serie está vacía: no hay nada que pronosticar.");
        }
        for (int t = 0; t < serie.length; t++) {
            double y = serie[t];
            if (!Double.isFinite(y) || y < 0) {
                throw new IllegalArgumentException("Valor inválido en la posición " + (t + 1) + " de la serie: " + y
                        + " (la serie corregida debe ser finita y estar recortada a 0).");
            }
        }
    }
}
