package co.livorno.controlstock.analitica.calculo;

import java.util.Arrays;

/**
 * Ajuste de Holt-Winters multiplicativo (m = 12) sobre una serie (resultado de {@link HoltWintersMultiplicativo}).
 * Inmutable: el arreglo de índices se copia al entrar y al salir.
 *
 * @param alfa          suavizado del nivel
 * @param beta          suavizado de la tendencia
 * @param gamma         suavizado de la estacionalidad
 * @param nivel         nivel_n
 * @param tendencia     tend_n
 * @param estacionales  los últimos 12 índices, s_{n−11} … s_n (posición j = s_{n−12+j+1})
 * @param sse           Σ_{t=1..n} (y_t − ŷ_t)², error de un paso adelante
 */
public record AjusteHoltWinters(double alfa, double beta, double gamma, double nivel, double tendencia,
        double[] estacionales, double sse) {

    public AjusteHoltWinters {
        if (estacionales.length != HoltWintersMultiplicativo.PERIODO) {
            throw new IllegalArgumentException("Se esperan " + HoltWintersMultiplicativo.PERIODO
                    + " índices estacionales y llegaron " + estacionales.length + ".");
        }
        estacionales = estacionales.clone();
    }

    @Override
    public double[] estacionales() {
        return estacionales.clone();
    }

    /**
     * pronóstico_{n+h} = max(0, (nivel_n + h·tend_n) × s_{n−12+((h−1) mod 12)+1}) para h = 1..horizonte.
     *
     * @throws IllegalArgumentException si el horizonte es negativo
     */
    public double[] pronosticar(int horizonte) {
        if (horizonte < 0) {
            throw new IllegalArgumentException("El horizonte no puede ser negativo: " + horizonte);
        }
        double[] pronostico = new double[horizonte];
        for (int h = 1; h <= horizonte; h++) {
            double valor = (nivel + h * tendencia) * estacionales[(h - 1) % HoltWintersMultiplicativo.PERIODO];
            pronostico[h - 1] = Math.max(0.0, valor);
        }
        return pronostico;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof AjusteHoltWinters o
                && Double.compare(alfa, o.alfa) == 0
                && Double.compare(beta, o.beta) == 0
                && Double.compare(gamma, o.gamma) == 0
                && Double.compare(nivel, o.nivel) == 0
                && Double.compare(tendencia, o.tendencia) == 0
                && Arrays.equals(estacionales, o.estacionales)
                && Double.compare(sse, o.sse) == 0;
    }

    @Override
    public int hashCode() {
        int h = Double.hashCode(alfa);
        h = 31 * h + Double.hashCode(beta);
        h = 31 * h + Double.hashCode(gamma);
        h = 31 * h + Double.hashCode(nivel);
        h = 31 * h + Double.hashCode(tendencia);
        h = 31 * h + Arrays.hashCode(estacionales);
        return 31 * h + Double.hashCode(sse);
    }

    @Override
    public String toString() {
        return "AjusteHoltWinters[α=" + alfa + ", β=" + beta + ", γ=" + gamma + ", nivel=" + nivel + ", tendencia="
                + tendencia + ", estacionales=" + Arrays.toString(estacionales) + ", sse=" + sse + "]";
    }
}
