package co.livorno.controlstock.analitica.calculo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Serie de demanda diaria de inventario de una variante (ADR-009), inmutable.
 *
 * <pre>
 * n = min(ventana, días desde la creación hasta el corte)   (0 si se creó el día del corte o después)
 * días = [corte − n, corte)   (excluye el día en curso)
 * serie[i] = neto del día (vendidas − devueltas aptas); días sin filas = 0 ; negativos → 0
 * d̄ = Σ serie / n ; s = sqrt( Σ (x − d̄)² / (n − 1) )   (n = 0 → d̄ = 0, s = 0 ; n = 1 → s = 0)
 * </pre>
 *
 * El día de creación cuenta como día de historia (puede tener ventas): una variante creada el 4 y analizada con corte
 * el 14 tiene n = 10 (días 4 a 13).
 */
public final class SerieDemanda {

    private final int[] valores;
    private final long total;

    private SerieDemanda(int[] valores) {
        this.valores = valores;
        long suma = 0;
        for (int v : valores) {
            suma += v;
        }
        this.total = suma;
    }

    /**
     * Construye la serie a partir de las filas dispersas de la consulta (día → neto). Los días fuera de
     * {@code [corte − n, corte)} se ignoran; los netos negativos (devoluciones mayores que ventas) se recortan a 0.
     */
    public static SerieDemanda construir(Map<LocalDate, Integer> netoPorDia, LocalDate fechaCreacion,
            LocalDate corte, int ventanaDias) {
        Objects.requireNonNull(netoPorDia, "netoPorDia");
        Objects.requireNonNull(fechaCreacion, "fechaCreacion");
        Objects.requireNonNull(corte, "corte");
        if (ventanaDias < 1) {
            throw new IllegalArgumentException("La ventana de demanda debe ser de al menos 1 día.");
        }
        long diasDesdeCreacion = ChronoUnit.DAYS.between(fechaCreacion, corte);
        int n = (int) Math.max(0, Math.min(ventanaDias, diasDesdeCreacion));
        LocalDate inicio = corte.minusDays(n);
        int[] serie = new int[n];
        for (Map.Entry<LocalDate, Integer> fila : netoPorDia.entrySet()) {
            LocalDate dia = fila.getKey();
            if (dia == null || dia.isBefore(inicio) || !dia.isBefore(corte)) {
                continue;
            }
            int neto = fila.getValue() == null ? 0 : fila.getValue();
            serie[(int) ChronoUnit.DAYS.between(inicio, dia)] = Math.max(0, neto);
        }
        return new SerieDemanda(serie);
    }

    /** Serie con los valores dados (uno por día, del más antiguo al más reciente); negativos → 0. */
    public static SerieDemanda de(int... valores) {
        Objects.requireNonNull(valores, "valores");
        return new SerieDemanda(Arrays.stream(valores).map(v -> Math.max(0, v)).toArray());
    }

    /** n: días de historia en la ventana. */
    public int dias() {
        return valores.length;
    }

    /** Σ serie (unidades). */
    public long total() {
        return total;
    }

    /** d̄ = Σ serie / n (0 si n = 0). */
    public double media() {
        return valores.length == 0 ? 0.0 : (double) total / valores.length;
    }

    /** Desviación estándar muestral (n − 1); 0 si n ≤ 1. */
    public double desviacion() {
        int n = valores.length;
        if (n <= 1) {
            return 0.0;
        }
        double media = media();
        double suma = 0.0;
        for (int v : valores) {
            double d = v - media;
            suma += d * d;
        }
        return Math.sqrt(suma / (n - 1));
    }

    /** Copia de la serie (día más antiguo primero). */
    public int[] valores() {
        return valores.clone();
    }

    @Override
    public String toString() {
        return "SerieDemanda[n=" + valores.length + ", total=" + total + "]";
    }
}
