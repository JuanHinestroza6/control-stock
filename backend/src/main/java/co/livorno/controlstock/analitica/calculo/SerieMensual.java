package co.livorno.controlstock.analitica.calculo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Serie mensual de demanda de inventario de un producto (ADR-010, decisión 1), inmutable.
 *
 * <pre>
 * primerMes = mes de creadoEn si se creó el día 1 a las 00:00; si no, el mes siguiente (primer mes completo)
 * meses     = [mesActual − n, mesActual)   con n = min(maxMeses, meses de primerMes a mesActual)   (0 si es negativo)
 * y_m       = neto del mes (vendidas − devueltas aptas); meses sin filas = 0 ; negativos → 0
 * </pre>
 *
 * El mes en curso nunca entra (está incompleto). Ejemplo: producto creado el 2026-08-15 10:00, mes actual 2026-10 →
 * primerMes = 2026-09, n = 1 (solo septiembre).
 */
public final class SerieMensual {

    private final YearMonth mesInicial;
    private final int[] valores;
    private final long total;

    private SerieMensual(YearMonth mesInicial, int[] valores) {
        this.mesInicial = mesInicial;
        this.valores = valores;
        long suma = 0;
        for (int v : valores) {
            suma += v;
        }
        this.total = suma;
    }

    /**
     * Construye la serie a partir de las filas dispersas de la consulta (mes → neto). Los meses fuera de la serie se
     * ignoran; los netos negativos (devoluciones aptas de ventas de meses anteriores) se recortan a 0.
     *
     * @param mesActual mes del reloj (excluido de la serie)
     * @param maxMeses  máximo de meses de historia (app.pronostico.meses-historia, 36)
     */
    public static SerieMensual construir(Map<YearMonth, Integer> netoPorMes, LocalDateTime productoCreadoEn,
            YearMonth mesActual, int maxMeses) {
        Objects.requireNonNull(netoPorMes, "netoPorMes");
        Objects.requireNonNull(productoCreadoEn, "productoCreadoEn");
        Objects.requireNonNull(mesActual, "mesActual");
        if (maxMeses < 1) {
            throw new IllegalArgumentException("La serie mensual debe admitir al menos 1 mes.");
        }
        YearMonth primerMes = primerMesCompleto(productoCreadoEn);
        long disponibles = primerMes.until(mesActual, ChronoUnit.MONTHS);
        int n = (int) Math.max(0, Math.min(maxMeses, disponibles));
        YearMonth inicio = mesActual.minusMonths(n);
        int[] serie = new int[n];
        for (Map.Entry<YearMonth, Integer> fila : netoPorMes.entrySet()) {
            YearMonth mes = fila.getKey();
            if (mes == null || mes.isBefore(inicio) || !mes.isBefore(mesActual)) {
                continue;
            }
            int neto = fila.getValue() == null ? 0 : fila.getValue();
            serie[(int) inicio.until(mes, ChronoUnit.MONTHS)] = Math.max(0, neto);
        }
        return new SerieMensual(inicio, serie);
    }

    /** Serie con los valores dados (uno por mes, desde {@code mesInicial}); negativos → 0. */
    public static SerieMensual de(YearMonth mesInicial, int... valores) {
        Objects.requireNonNull(mesInicial, "mesInicial");
        Objects.requireNonNull(valores, "valores");
        return new SerieMensual(mesInicial, Arrays.stream(valores).map(v -> Math.max(0, v)).toArray());
    }

    /** Primer mes completo de un producto: el de creación solo si se creó el día 1 a las 00:00. */
    static YearMonth primerMesCompleto(LocalDateTime creadoEn) {
        YearMonth mes = YearMonth.from(creadoEn);
        boolean inicioExacto = creadoEn.getDayOfMonth() == 1 && creadoEn.toLocalTime().equals(LocalTime.MIDNIGHT);
        return inicioExacto ? mes : mes.plusMonths(1);
    }

    /** Primer mes de la serie (si está vacía, el mes actual con que se construyó). */
    public YearMonth mesInicial() {
        return mesInicial;
    }

    /** n: meses completos en la serie. */
    public int meses() {
        return valores.length;
    }

    /** Mes de la posición i (0 = el más antiguo). */
    public YearMonth mes(int i) {
        Objects.checkIndex(i, valores.length);
        return mesInicial.plusMonths(i);
    }

    /** y_i: demanda observada del mes de la posición i. */
    public int valor(int i) {
        return valores[i];
    }

    /** Σ y (unidades). */
    public long total() {
        return total;
    }

    /** true si algún mes de la serie es 0 (Holt-Winters multiplicativo no compite: MES_EN_CERO). */
    public boolean tieneMesEnCero() {
        return Arrays.stream(valores).anyMatch(v -> v == 0);
    }

    /** Copia de la serie (mes más antiguo primero). */
    public int[] valores() {
        return valores.clone();
    }

    @Override
    public String toString() {
        return "SerieMensual[desde=" + mesInicial + ", n=" + valores.length + ", total=" + total + "]";
    }
}
