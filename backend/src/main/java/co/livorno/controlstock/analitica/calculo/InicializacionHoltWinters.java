package co.livorno.controlstock.analitica.calculo;

import java.util.Arrays;

/**
 * Estado inicial de Holt-Winters multiplicativo calculado con los dos primeros años de la serie (ver
 * {@link HoltWintersMultiplicativo#inicializar}). Inmutable: los arreglos se copian al entrar y al salir.
 *
 * @param nivel      nivel_0 = ȳ1 − 6,5 · tend_0
 * @param tendencia  tend_0 = (ȳ2 − ȳ1) / 12
 * @param razones    r_i = y_i / (nivel_0 + i · tend_0), i = 1..12 (posición 0 = enero de la serie)
 * @param indices    s_i = r_i × 12 / Σ r_j (media 1)
 */
public record InicializacionHoltWinters(double nivel, double tendencia, double[] razones, double[] indices) {

    public InicializacionHoltWinters {
        razones = razones.clone();
        indices = indices.clone();
    }

    @Override
    public double[] razones() {
        return razones.clone();
    }

    @Override
    public double[] indices() {
        return indices.clone();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof InicializacionHoltWinters o
                && Double.compare(nivel, o.nivel) == 0
                && Double.compare(tendencia, o.tendencia) == 0
                && Arrays.equals(razones, o.razones)
                && Arrays.equals(indices, o.indices);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Double.hashCode(nivel) + Double.hashCode(tendencia)) + Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "InicializacionHoltWinters[nivel=" + nivel + ", tendencia=" + tendencia + ", indices="
                + Arrays.toString(indices) + "]";
    }
}
