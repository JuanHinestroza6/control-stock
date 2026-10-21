package co.livorno.controlstock.analitica.calculo;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Objects;

/**
 * Resultado de {@link CorrectorCensura#corregir}: serie observada, disponibilidad y serie corregida de un producto, mes a
 * mes (se guardan las tres para explicar el pronóstico; ADR-010, decisiones 1 y 9). Inmutable.
 */
public final class SerieCorregida {

    private final YearMonth mesInicial;
    private final int[] observada;
    private final double[] disponibilidad;
    private final double[] corregida;

    SerieCorregida(YearMonth mesInicial, int[] observada, double[] disponibilidad, double[] corregida) {
        this.mesInicial = Objects.requireNonNull(mesInicial, "mesInicial");
        this.observada = observada.clone();
        this.disponibilidad = disponibilidad.clone();
        this.corregida = corregida.clone();
    }

    public YearMonth mesInicial() {
        return mesInicial;
    }

    public int meses() {
        return observada.length;
    }

    /** Mes de la posición i (0 = el más antiguo). */
    public YearMonth mes(int i) {
        Objects.checkIndex(i, observada.length);
        return mesInicial.plusMonths(i);
    }

    /** y_m observada (copia). */
    public int[] observada() {
        return observada.clone();
    }

    /** disp_m en [0, 1], antes de aplicar el piso (copia). */
    public double[] disponibilidad() {
        return disponibilidad.clone();
    }

    /** corregida_m = y_m / max(disp_m, piso), 0 si y_m = 0 (copia). Es la serie que se pronostica. */
    public double[] corregida() {
        return corregida.clone();
    }

    public long totalObservada() {
        long suma = 0;
        for (int v : observada) {
            suma += v;
        }
        return suma;
    }

    public double totalCorregida() {
        return Arrays.stream(corregida).sum();
    }

    @Override
    public String toString() {
        return "SerieCorregida[desde=" + mesInicial + ", n=" + observada.length + ", observada=" + totalObservada()
                + ", corregida=" + totalCorregida() + "]";
    }
}
