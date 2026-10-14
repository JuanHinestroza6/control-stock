package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Clasificación ABC (Pareto) por ingreso neto, función pura (ADR-009).
 *
 * <pre>
 * Ordenar por ingreso desc, desempate por varianteId asc. total = Σ ingreso.
 * participacion_i = ingreso_i / total ; acumulado_i = (Σ_{j≤i} ingreso_j) / total   (escala 6, HALF_UP)
 * A si posicion = 1 o acumulado ≤ corteA ; B si acumulado ≤ corteB ; C en otro caso.
 * ingreso 0 → C ; total 0 → todas C (participación y acumulado 0).
 * </pre>
 *
 * El acumulado se calcula con el ingreso acumulado exacto y se redondea una sola vez, de modo que el último siempre es
 * 1,000000 (sumar participaciones ya redondeadas podría dar 0,999999).
 */
public final class ClasificadorAbc {

    static final int ESCALA = 6;

    private static final Comparator<IngresoVariante> ORDEN = Comparator
            .comparing(IngresoVariante::ingresoNeto, Comparator.reverseOrder())
            .thenComparingLong(IngresoVariante::varianteId);

    private ClasificadorAbc() {
    }

    /**
     * Clasifica las variantes. Devuelve la lista ordenada por posición (vacía si la entrada es vacía).
     *
     * @throws IllegalArgumentException si hay ingresos nulos o negativos, ids repetidos o cortes fuera de
     *                                  {@code 0 < corteA < corteB < 1}
     */
    public static List<ClasificacionAbc> clasificar(List<IngresoVariante> ingresos, BigDecimal corteA,
            BigDecimal corteB) {
        Objects.requireNonNull(ingresos, "ingresos");
        validarCortes(corteA, corteB);
        validarEntradas(ingresos);

        List<IngresoVariante> ordenados = new ArrayList<>(ingresos);
        ordenados.sort(ORDEN);
        BigDecimal total = ordenados.stream().map(IngresoVariante::ingresoNeto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean totalCero = total.signum() == 0;

        List<ClasificacionAbc> resultado = new ArrayList<>(ordenados.size());
        BigDecimal ingresoAcumulado = BigDecimal.ZERO;
        int posicion = 0;
        for (IngresoVariante entrada : ordenados) {
            posicion++;
            BigDecimal ingreso = entrada.ingresoNeto();
            ingresoAcumulado = ingresoAcumulado.add(ingreso);
            BigDecimal participacion = totalCero ? cero() : ingreso.divide(total, ESCALA, RoundingMode.HALF_UP);
            BigDecimal acumulado = totalCero ? cero()
                    : ingresoAcumulado.divide(total, ESCALA, RoundingMode.HALF_UP);
            char clase = clase(posicion, ingreso, acumulado, corteA, corteB);
            resultado.add(new ClasificacionAbc(entrada.varianteId(), ingreso.setScale(2, RoundingMode.HALF_UP),
                    posicion, participacion, acumulado, clase));
        }
        return List.copyOf(resultado);
    }

    private static char clase(int posicion, BigDecimal ingreso, BigDecimal acumulado, BigDecimal corteA,
            BigDecimal corteB) {
        if (ingreso.signum() == 0) {
            return 'C';
        }
        if (posicion == 1 || acumulado.compareTo(corteA) <= 0) {
            return 'A';
        }
        return acumulado.compareTo(corteB) <= 0 ? 'B' : 'C';
    }

    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(ESCALA);
    }

    private static void validarCortes(BigDecimal corteA, BigDecimal corteB) {
        if (corteA == null || corteB == null || corteA.signum() <= 0 || corteA.compareTo(corteB) >= 0
                || corteB.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Los cortes ABC deben cumplir 0 < A < B < 1.");
        }
    }

    private static void validarEntradas(List<IngresoVariante> ingresos) {
        Set<Long> ids = new HashSet<>();
        for (IngresoVariante entrada : ingresos) {
            if (entrada == null || entrada.ingresoNeto() == null) {
                throw new IllegalArgumentException("Cada variante debe traer su ingreso neto.");
            }
            if (entrada.ingresoNeto().signum() < 0) {
                throw new IllegalArgumentException(
                        "El ingreso neto no puede ser negativo (variante " + entrada.varianteId() + ").");
            }
            if (!ids.add(entrada.varianteId())) {
                throw new IllegalArgumentException("Variante repetida en el ABC: " + entrada.varianteId() + ".");
            }
        }
    }
}
