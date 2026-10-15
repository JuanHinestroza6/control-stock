package co.livorno.controlstock.analitica.calculo;

/**
 * Distribución de Poisson para demanda intermitente (ADR-009), funciones puras.
 *
 * <pre>
 * P(X = 0) = e^(−λ) ; P(X = k) = P(X = k − 1) × λ / k      (suma acumulada iterativa)
 * cuantil(λ, p) = mín r tal que P(X ≤ r) ≥ p
 * </pre>
 *
 * Se usa con λ = μL &lt; 5, donde no hay problemas numéricos. La comparación tolera 1e-12 para que un acumulado que
 * en aritmética exacta iguala a p no quede un paso arriba por el redondeo de los double.
 */
public final class DistribucionPoisson {

    static final double TOLERANCIA = 1e-12;

    private DistribucionPoisson() {
    }

    /** P(X ≤ r) para X ~ Poisson(λ); 0 si r &lt; 0. */
    public static double acumulada(double lambda, int r) {
        validarLambda(lambda);
        if (r < 0) {
            return 0.0;
        }
        double termino = Math.exp(-lambda);
        double acumulado = termino;
        for (int k = 1; k <= r; k++) {
            termino = termino * lambda / k;
            acumulado += termino;
        }
        return Math.min(1.0, acumulado);
    }

    /**
     * Menor r con P(X ≤ r) ≥ p.
     *
     * @throws IllegalArgumentException si λ es negativo, no finito o tan grande que e^(−λ) se anula, o si p no está
     *                                  en (0, 1)
     */
    public static int cuantil(double lambda, double p) {
        validarLambda(lambda);
        if (!(p > 0.0 && p < 1.0)) {
            throw new IllegalArgumentException("El nivel de servicio debe estar en (0, 1): " + p);
        }
        double termino = Math.exp(-lambda);
        if (termino == 0.0) {
            throw new IllegalArgumentException("λ demasiado grande para la suma iterativa: " + lambda);
        }
        double acumulado = termino;
        int r = 0;
        // Cota de seguridad muy por encima de cualquier cuantil razonable (λ + 50·√λ + 50).
        int limite = (int) Math.ceil(lambda + 50 * Math.sqrt(lambda) + 50);
        while (acumulado < p - TOLERANCIA && r < limite) {
            r++;
            termino = termino * lambda / r;
            acumulado += termino;
        }
        return r;
    }

    private static void validarLambda(double lambda) {
        if (!(lambda >= 0.0) || Double.isInfinite(lambda)) {
            throw new IllegalArgumentException("λ debe ser un número finito ≥ 0: " + lambda);
        }
    }
}
