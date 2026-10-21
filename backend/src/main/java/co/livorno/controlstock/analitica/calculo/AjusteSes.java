package co.livorno.controlstock.analitica.calculo;

import java.util.Arrays;

/**
 * Ajuste de suavizado exponencial simple sobre una serie (resultado de {@link SuavizadoExponencial}).
 *
 * @param alfa  constante de suavizado usada
 * @param nivel nivel_n, último nivel; es el pronóstico de todo el horizonte
 * @param sse   Σ_{t=2..n} (y_t − nivel_{t−1})², error de un paso adelante (0 con n = 1)
 */
public record AjusteSes(double alfa, double nivel, double sse) {

    /**
     * pronóstico_{n+h} = max(0, nivel_n) para h = 1..horizonte (constante).
     *
     * @throws IllegalArgumentException si el horizonte es negativo
     */
    public double[] pronosticar(int horizonte) {
        if (horizonte < 0) {
            throw new IllegalArgumentException("El horizonte no puede ser negativo: " + horizonte);
        }
        double[] pronostico = new double[horizonte];
        Arrays.fill(pronostico, Math.max(0.0, nivel));
        return pronostico;
    }
}
