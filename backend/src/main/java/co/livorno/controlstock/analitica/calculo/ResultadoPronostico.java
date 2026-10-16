package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de {@link SelectorPronostico#seleccionar} para un producto: método elegido, parámetros, evidencia del
 * backtest y pronóstico de los próximos meses. Las cantidades y los parámetros salen en {@link BigDecimal} con escala 2
 * HALF_UP y los WAPE con escala 4 HALF_UP (columnas DECIMAL(10,2), DECIMAL(3,2) y DECIMAL(7,4) de la V15).
 *
 * @param metodo            SES, HOLT_WINTERS o SIN_HISTORIA
 * @param mesesHistoria     n, meses de la serie corregida
 * @param alfa              α del método elegido ajustado con toda la serie (null en SIN_HISTORIA)
 * @param beta              β (solo HOLT_WINTERS)
 * @param gamma             γ (solo HOLT_WINTERS)
 * @param wapeSes           WAPE del backtest de SES (null sin backtest o con Σ y = 0)
 * @param wapeHw            WAPE del backtest de HW (null si HW no compitió)
 * @param errorWape         WAPE del método elegido (null sin backtest o con Σ y = 0)
 * @param motivoSinHw       por qué HW no compitió (null si compitió, o en SIN_HISTORIA)
 * @param pronostico        h = 1..horizonte (mes actual y siguientes); vacío en SIN_HISTORIA
 * @param backtestSes       pronósticos de SES para los meses n − 5 … n (dos orígenes × 3 meses); vacío sin backtest
 * @param backtestHw        ídem para HW; vacío si HW no compitió
 * @param errorAbsolutoBacktest Σ |y − ŷ| del método elegido sobre los 6 meses del backtest (0 sin backtest), sin
 *                              redondear, para el WAPE global de la ejecución
 * @param realBacktest      Σ y de los 6 meses del backtest (0 sin backtest), sin redondear
 */
public record ResultadoPronostico(
        MetodoPronostico metodo,
        int mesesHistoria,
        BigDecimal alfa,
        BigDecimal beta,
        BigDecimal gamma,
        BigDecimal wapeSes,
        BigDecimal wapeHw,
        BigDecimal errorWape,
        MotivoSinHoltWinters motivoSinHw,
        List<BigDecimal> pronostico,
        List<BigDecimal> backtestSes,
        List<BigDecimal> backtestHw,
        double errorAbsolutoBacktest,
        double realBacktest) {

    public ResultadoPronostico {
        Objects.requireNonNull(metodo, "metodo");
        pronostico = List.copyOf(pronostico);
        backtestSes = List.copyOf(backtestSes);
        backtestHw = List.copyOf(backtestHw);
        if ((metodo == MetodoPronostico.HOLT_WINTERS) != (beta != null)) {
            throw new IllegalArgumentException("β (y γ) existen solo con HOLT_WINTERS.");
        }
        if (metodo == MetodoPronostico.HOLT_WINTERS && motivoSinHw != null) {
            throw new IllegalArgumentException("Un producto con HOLT_WINTERS no tiene motivo sin HW.");
        }
        if ((metodo == MetodoPronostico.SIN_HISTORIA) != pronostico.isEmpty()) {
            throw new IllegalArgumentException("Solo SIN_HISTORIA no tiene pronóstico.");
        }
    }

    /** true si hubo backtest (n ≥ 9): sus sumas entran en el WAPE global de la ejecución. */
    public boolean conBacktest() {
        return !backtestSes.isEmpty();
    }
}
