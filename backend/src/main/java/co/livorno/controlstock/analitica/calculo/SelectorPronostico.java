package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Elige entre SES y Holt-Winters multiplicativo para la serie mensual corregida de un producto mediante backtesting con
 * origen móvil (ADR-010, decisión 2), y pronostica los próximos meses con el método elegido. Funciones puras.
 *
 * <pre>
 * n = meses de la serie corregida
 * n &lt; 3        → SIN_HISTORIA (sin pronóstico; ROP y sugerencia usan la demanda histórica)
 * 3 ≤ n &lt; 9    → SES sin backtest (WAPE null), motivo POCA_HISTORIA
 * 9 ≤ n &lt; 30   → solo SES, con WAPE del backtest, motivo POCA_HISTORIA
 * n ≥ 30       → SES vs HW multiplicativo, salvo MES_EN_CERO o NIVEL_NO_POSITIVO (solo SES)
 *
 * Backtest: orígenes o ∈ {n − 6, n − 3}; con y_1..y_o se ajusta (rejilla incluida) y se pronostican o+1..o+3
 *           → 6 pronósticos para los meses n − 5 … n.
 * WAPE = Σ |y − ŷ| / Σ y sobre esos 6 puntos (Σ y = 0 → WAPE null → SES).
 * Elegido = menor WAPE; si WAPE_SES − WAPE_HW &lt; 0,001 → SES (parsimonia).
 * El elegido se reajusta con toda la serie y pronostica h = 1..horizonte (mes actual y siguientes).
 * </pre>
 *
 * Con n ≥ 30 el entrenamiento más corto (n − 6) tiene ≥ 24 meses: las dos temporadas que exige la inicialización de HW.
 * Los entrenamientos son prefijos de la serie, así que MES_EN_CERO y el nivel inicial (que solo usa los dos primeros
 * años) se evalúan una vez sobre la serie completa. NIVEL_NO_POSITIVO también aparece si en algún entrenamiento del
 * backtest, o en el reajuste final de un HW ganador, la rejilla entera lleva el nivel a ≤ 0: HW no compite (o no se
 * usa) y el producto queda en SES con ese motivo.
 *
 * Ejemplo WAPE a mano: reales 10, 20, 30; pronóstico 12, 18, 33 → (2 + 2 + 3) / 60 = 7 / 60 = 0,1167.
 */
public final class SelectorPronostico {

    /** Meses mínimos para pronosticar (por debajo, SIN_HISTORIA). */
    public static final int MESES_MINIMOS_PRONOSTICO = 3;
    /** Meses mínimos para el backtest (el primer origen, n − 6, deja ≥ 3 meses de entrenamiento). */
    public static final int MESES_MINIMOS_BACKTEST = 9;
    /** Meses mínimos para que HW compita: el entrenamiento más corto (n − 6) debe tener 24 meses. */
    public static final int MESES_MINIMOS_HOLT_WINTERS = 30;
    /** Meses que pronostica cada origen del backtest. */
    public static final int PASOS_BACKTEST = 3;
    /** Distancia de cada origen al final de la serie: o = n − 6 y o = n − 3. */
    static final int[] RETROCESO_ORIGENES = {6, 3};
    /** Meses evaluados en el backtest (2 orígenes × 3 pasos). */
    public static final int MESES_BACKTEST = RETROCESO_ORIGENES.length * PASOS_BACKTEST;
    /** Horizonte por defecto: mes actual y los dos siguientes. */
    public static final int HORIZONTE = 3;
    /** Diferencia de WAPE por debajo de la cual se considera empate y gana SES. */
    public static final double TOLERANCIA_EMPATE_WAPE = 0.001;

    private SelectorPronostico() {
    }

    /** {@link #seleccionar(double[], int)} con el horizonte por defecto (3 meses). */
    public static ResultadoPronostico seleccionar(double[] serie) {
        return seleccionar(serie, HORIZONTE);
    }

    /**
     * Selecciona el método y pronostica.
     *
     * @param serie     serie mensual corregida y_1..y_n (n ≥ 0; valores finitos y ≥ 0)
     * @param horizonte meses a pronosticar con el método elegido (≥ 1)
     * @throws IllegalArgumentException si el horizonte es menor que 1 o la serie trae un valor negativo o no finito
     */
    public static ResultadoPronostico seleccionar(double[] serie, int horizonte) {
        Objects.requireNonNull(serie, "serie");
        if (horizonte < 1) {
            throw new IllegalArgumentException("El horizonte debe ser al menos 1: " + horizonte);
        }
        int n = serie.length;
        if (n > 0) {
            SuavizadoExponencial.validar(serie);
        }
        if (n < MESES_MINIMOS_PRONOSTICO) {
            return new ResultadoPronostico(MetodoPronostico.SIN_HISTORIA, n, null, null, null, null, null, null, null,
                    List.of(), List.of(), List.of(), 0, 0);
        }
        if (n < MESES_MINIMOS_BACKTEST) {
            AjusteSes ses = SuavizadoExponencial.ajustar(serie);
            return new ResultadoPronostico(MetodoPronostico.SES, n, escala2(ses.alfa()), null, null, null, null, null,
                    MotivoSinHoltWinters.POCA_HISTORIA, escala2(ses.pronosticar(horizonte)), List.of(), List.of(), 0,
                    0);
        }

        double[] reales = Arrays.copyOfRange(serie, n - MESES_BACKTEST, n);
        double[] backtestSes = backtestSes(serie);
        OptionalDouble wapeSes = wape(reales, backtestSes);

        MotivoSinHoltWinters motivo;
        double[] backtestHw = null;
        if (n < MESES_MINIMOS_HOLT_WINTERS) {
            motivo = MotivoSinHoltWinters.POCA_HISTORIA;
        } else {
            motivo = HoltWintersMultiplicativo.motivoSinHoltWinters(serie).orElse(null);
            if (motivo == null) {
                ResultadoBacktestHw hw = backtestHw(serie);
                backtestHw = hw.pronosticos();
                motivo = hw.motivo();
            }
        }
        OptionalDouble wapeHw = backtestHw == null ? OptionalDouble.empty() : wape(reales, backtestHw);

        AjusteHoltWinters ajusteHw = null;
        if (eligeHoltWinters(wapeSes, wapeHw)) {
            ResultadoHoltWinters total = HoltWintersMultiplicativo.ajustar(serie);
            if (total.elegible()) {
                ajusteHw = total.ajuste();
            } else {
                motivo = total.motivo();  // el ganador del backtest no se puede reajustar: SES (NIVEL_NO_POSITIVO)
            }
        }

        List<BigDecimal> btSes = escala2(backtestSes);
        List<BigDecimal> btHw = backtestHw == null ? List.of() : escala2(backtestHw);
        double realBacktest = suma(reales);
        if (ajusteHw != null) {
            return new ResultadoPronostico(MetodoPronostico.HOLT_WINTERS, n, escala2(ajusteHw.alfa()),
                    escala2(ajusteHw.beta()), escala2(ajusteHw.gamma()), escala4(wapeSes), escala4(wapeHw),
                    escala4(wapeHw), null, escala2(ajusteHw.pronosticar(horizonte)), btSes, btHw,
                    errorAbsoluto(reales, backtestHw), realBacktest);
        }
        AjusteSes ses = SuavizadoExponencial.ajustar(serie);
        return new ResultadoPronostico(MetodoPronostico.SES, n, escala2(ses.alfa()), null, null, escala4(wapeSes),
                escala4(wapeHw), escala4(wapeSes), motivo, escala2(ses.pronosticar(horizonte)), btSes, btHw,
                errorAbsoluto(reales, backtestSes), realBacktest);
    }

    /**
     * WAPE = Σ |y − ŷ| / Σ y.
     *
     * @return vacío si Σ y = 0 (sin demanda real no hay error relativo; el selector elige SES)
     * @throws IllegalArgumentException si los arreglos están vacíos o tienen distinto largo
     */
    public static OptionalDouble wape(double[] reales, double[] pronosticos) {
        Objects.requireNonNull(reales, "reales");
        Objects.requireNonNull(pronosticos, "pronosticos");
        if (reales.length == 0 || reales.length != pronosticos.length) {
            throw new IllegalArgumentException("Se necesitan reales y pronósticos del mismo largo, no vacíos: "
                    + reales.length + " y " + pronosticos.length + ".");
        }
        double totalReal = suma(reales);
        if (totalReal == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(errorAbsoluto(reales, pronosticos) / totalReal);
    }

    /**
     * HW gana solo si ambos WAPE existen y el de SES lo supera en al menos 0,001; empate o WAPE null → SES.
     */
    static boolean eligeHoltWinters(OptionalDouble wapeSes, OptionalDouble wapeHw) {
        return wapeSes.isPresent() && wapeHw.isPresent()
                && wapeSes.getAsDouble() - wapeHw.getAsDouble() >= TOLERANCIA_EMPATE_WAPE;
    }

    /** Pronósticos de SES para los meses n − 5 … n: en cada origen se ajusta α con y_1..y_o y se pronostican 3. */
    private static double[] backtestSes(double[] serie) {
        int n = serie.length;
        double[] resultado = new double[MESES_BACKTEST];
        for (int k = 0; k < RETROCESO_ORIGENES.length; k++) {
            int origen = n - RETROCESO_ORIGENES[k];
            double[] p = SuavizadoExponencial.ajustar(Arrays.copyOf(serie, origen)).pronosticar(PASOS_BACKTEST);
            System.arraycopy(p, 0, resultado, k * PASOS_BACKTEST, PASOS_BACKTEST);
        }
        return resultado;
    }

    /** Ídem para HW; si en algún origen la rejilla entera se descarta, HW no compite (motivo del ajuste). */
    private static ResultadoBacktestHw backtestHw(double[] serie) {
        int n = serie.length;
        double[] resultado = new double[MESES_BACKTEST];
        for (int k = 0; k < RETROCESO_ORIGENES.length; k++) {
            int origen = n - RETROCESO_ORIGENES[k];
            ResultadoHoltWinters ajuste = HoltWintersMultiplicativo.ajustar(Arrays.copyOf(serie, origen));
            if (!ajuste.elegible()) {
                return new ResultadoBacktestHw(null, ajuste.motivo());
            }
            double[] p = ajuste.ajuste().pronosticar(PASOS_BACKTEST);
            System.arraycopy(p, 0, resultado, k * PASOS_BACKTEST, PASOS_BACKTEST);
        }
        return new ResultadoBacktestHw(resultado, null);
    }

    private record ResultadoBacktestHw(double[] pronosticos, MotivoSinHoltWinters motivo) {
    }

    private static double errorAbsoluto(double[] reales, double[] pronosticos) {
        double total = 0;
        for (int i = 0; i < reales.length; i++) {
            total += Math.abs(reales[i] - pronosticos[i]);
        }
        return total;
    }

    private static double suma(double[] valores) {
        double total = 0;
        for (double v : valores) {
            total += v;
        }
        return total;
    }

    private static BigDecimal escala2(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<BigDecimal> escala2(double[] valores) {
        List<BigDecimal> lista = new ArrayList<>(valores.length);
        for (double v : valores) {
            lista.add(escala2(v));
        }
        return lista;
    }

    private static BigDecimal escala4(OptionalDouble valor) {
        return valor.isPresent() ? BigDecimal.valueOf(valor.getAsDouble()).setScale(4, RoundingMode.HALF_UP) : null;
    }
}
