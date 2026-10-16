package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SelectorPronosticoTest {

    /** Factores de mes de la semilla (ene … dic) normalizados a media 1: S_i = f_i × 12 / 12,15. */
    private static final double[] S = normalizar(
            new double[] {0.8, 0.7, 0.8, 0.85, 1.0, 1.25, 1.0, 0.9, 0.95, 1.0, 1.3, 1.6});

    private static double[] normalizar(double[] f) {
        double suma = Arrays.stream(f).sum();
        return Arrays.stream(f).map(x -> x * f.length / suma).toArray();
    }

    /** y_t = (base + pendiente·t) × S_t, t = 1..n (t = 1 es enero). */
    private static double[] estacional(int n, double base, double pendiente) {
        double[] y = new double[n];
        for (int t = 1; t <= n; t++) {
            y[t - 1] = (base + pendiente * t) * S[(t - 1) % 12];
        }
        return y;
    }

    private static double[] repetir(double valor, int veces) {
        double[] y = new double[veces];
        Arrays.fill(y, valor);
        return y;
    }

    private static double[] unir(double[]... partes) {
        return Arrays.stream(partes).flatMapToDouble(Arrays::stream).toArray();
    }

    private static List<BigDecimal> bd(String... valores) {
        return Arrays.stream(valores).map(BigDecimal::new).toList();
    }

    // ------------------------------------------------------------------ WAPE y desempate

    /*
     * Ejemplo del plan: reales 10, 20, 30 ; pronóstico 12, 18, 33
     *   Σ |y − ŷ| = 2 + 2 + 3 = 7 ; Σ y = 60 → WAPE = 7 / 60 = 0,11667 ≈ 0,1167
     */
    @Test
    void wape_ejemploAMano() {
        OptionalDouble wape = SelectorPronostico.wape(new double[] {10, 20, 30}, new double[] {12, 18, 33});

        assertThat(wape).isPresent();
        assertThat(wape.getAsDouble()).isCloseTo(7.0 / 60, within(1e-12));
        assertThat(wape.getAsDouble()).isCloseTo(0.1167, within(5e-5));
    }

    /* Σ y = 0 → no hay error relativo: WAPE vacío (el selector elige SES). */
    @Test
    void wape_sumaDeRealesCero_vacio() {
        assertThat(SelectorPronostico.wape(new double[] {0, 0, 0}, new double[] {1, 2, 3})).isEmpty();
    }

    @Test
    void wape_largosDistintosOVacios_rechaza() {
        assertThatThrownBy(() -> SelectorPronostico.wape(new double[] {1, 2}, new double[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectorPronostico.wape(new double[0], new double[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /*
     * Parsimonia: HW gana solo si WAPE_SES − WAPE_HW ≥ 0,001.
     *   0,2000 vs 0,1995 → diferencia 0,0005 < 0,001 → SES
     *   0,2000 vs 0,1980 → diferencia 0,0020       → HW
     *   HW peor, o algún WAPE null (Σ y = 0)       → SES
     */
    @Test
    void eligeHoltWinters_empateMenorAUnMilesimo_gananSes() {
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.of(0.2), OptionalDouble.of(0.1995))).isFalse();
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.of(0.2), OptionalDouble.of(0.198))).isTrue();
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.of(0.2), OptionalDouble.of(0.2))).isFalse();
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.of(0.1), OptionalDouble.of(0.3))).isFalse();
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.empty(), OptionalDouble.of(0.1))).isFalse();
        assertThat(SelectorPronostico.eligeHoltWinters(OptionalDouble.of(0.1), OptionalDouble.empty())).isFalse();
    }

    // ------------------------------------------------------------------ tramos de n

    /* n < 3 (incluida la serie vacía de un producto nuevo) → SIN_HISTORIA, sin pronóstico ni backtest. */
    @Test
    void seleccionar_menosDeTresMeses_sinHistoria() {
        for (double[] serie : new double[][] {{}, {5}, {5, 7}}) {
            ResultadoPronostico r = SelectorPronostico.seleccionar(serie);

            assertThat(r.metodo()).isEqualTo(MetodoPronostico.SIN_HISTORIA);
            assertThat(r.mesesHistoria()).isEqualTo(serie.length);
            assertThat(r.pronostico()).isEmpty();
            assertThat(r.alfa()).isNull();
            assertThat(r.errorWape()).isNull();
            assertThat(r.motivoSinHw()).isNull();
            assertThat(r.conBacktest()).isFalse();
            assertThat(r.realBacktest()).isZero();
        }
    }

    /*
     * 3 ≤ n < 9 → SES sin backtest. Serie 10, 12, 14:
     *   SSE(α) = (12 − 10)² + (14 − (10 + 2α))² = 4 + (4 − 2α)² → mínimo en α = 0,9: 4 + 2,2² = 8,84
     *   niveles: 10 → 10 + 0,9 × 2 = 11,8 → 0,9 × 14 + 0,1 × 11,8 = 13,78 → pronóstico 13,78 (constante)
     */
    @Test
    void seleccionar_entreTresYOchoMeses_sesSinBacktest() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(new double[] {10, 12, 14});

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.mesesHistoria()).isEqualTo(3);
        assertThat(r.alfa()).isEqualByComparingTo("0.90");
        assertThat(r.beta()).isNull();
        assertThat(r.gamma()).isNull();
        assertThat(r.pronostico()).isEqualTo(bd("13.78", "13.78", "13.78"));
        assertThat(r.wapeSes()).isNull();
        assertThat(r.wapeHw()).isNull();
        assertThat(r.errorWape()).isNull();
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.POCA_HISTORIA);
        assertThat(r.conBacktest()).isFalse();
        assertThat(r.backtestHw()).isEmpty();
        assertThat(SelectorPronostico.seleccionar(repetir(4, 8)).conBacktest()).isFalse();
    }

    /*
     * 9 ≤ n < 30 → solo SES con backtest de origen móvil. Serie (n = 9): 10 × 6, 20 × 3.
     *   Origen o = n − 6 = 3: y_1..y_3 = 10, 10, 10 → SSE 0 con todo α → α = 0,1, nivel 10
     *     → pronóstico de los meses 4, 5, 6 = 10, 10, 10 ; reales 10, 10, 10 → errores 0, 0, 0
     *   Origen o = n − 3 = 6: y_1..y_6 = 10 × 6 → nivel 10 → meses 7, 8, 9 = 10, 10, 10 ; reales 20, 20, 20 → 10, 10, 10
     *   WAPE = (0 + 0 + 0 + 10 + 10 + 10) / (10 + 10 + 10 + 20 + 20 + 20) = 30 / 90 = 0,3333
     * Reajuste con toda la serie: el error del escalón 10 → 20 decae como 10·(1 − α)^k; el menor SSE es α = 0,9
     *   (errores 10, 1, 0,1) → nivel_9 = 20 − 10 × 0,1³ = 19,99 → pronóstico 19,99.
     */
    @Test
    void seleccionar_entreNueveYVeintinueveMeses_sesConBacktestAMano() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(unir(repetir(10, 6), repetir(20, 3)));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.POCA_HISTORIA);
        assertThat(r.backtestSes()).isEqualTo(bd("10.00", "10.00", "10.00", "10.00", "10.00", "10.00"));
        assertThat(r.backtestHw()).isEmpty();
        assertThat(r.wapeSes()).isEqualByComparingTo("0.3333");
        assertThat(r.errorWape()).isEqualByComparingTo("0.3333");
        assertThat(r.wapeHw()).isNull();
        assertThat(r.errorAbsolutoBacktest()).isCloseTo(30, within(1e-9));
        assertThat(r.realBacktest()).isCloseTo(90, within(1e-9));
        assertThat(r.alfa()).isEqualByComparingTo("0.90");
        assertThat(r.pronostico()).isEqualTo(bd("19.99", "19.99", "19.99"));
    }

    /* n = 29 con estacionalidad perfecta: HW todavía no compite (POCA_HISTORIA); n = 30 ya compite y gana. */
    @Test
    void seleccionar_bordeDeTreintaMeses_hwCompiteDesdeTreinta() {
        ResultadoPronostico con29 = SelectorPronostico.seleccionar(estacional(29, 100, 0));
        ResultadoPronostico con30 = SelectorPronostico.seleccionar(estacional(30, 100, 0));

        assertThat(con29.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(con29.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.POCA_HISTORIA);
        assertThat(con29.wapeSes()).isNotNull();
        assertThat(con29.wapeHw()).isNull();

        assertThat(con30.metodo()).isEqualTo(MetodoPronostico.HOLT_WINTERS);
        assertThat(con30.motivoSinHw()).isNull();
        assertThat(con30.backtestHw()).hasSize(6);
    }

    // ------------------------------------------------------------------ SES vs HW (n ≥ 30)

    /*
     * y_t = 100 × S_t, n = 36 (enero a diciembre × 3): HW reproduce la serie exactamente (prueba de A2) en cada
     * entrenamiento → los 6 pronósticos del backtest son exactos → WAPE_HW = 0. SES persigue la estación con retraso
     * → WAPE_SES > 0,001 → gana HW. SSE ≈ 0 con las 125 combinaciones → (0,1; 0,1; 0,1) por orden lexicográfico.
     * Pronóstico (meses 37..39 = enero, febrero, marzo): 100 × S = 100 × 0,8 × 12 / 12,15 = 79,01 ;
     *   100 × 0,7 × 12 / 12,15 = 69,14 ; 79,01.
     */
    @Test
    void seleccionar_estacionalPura_ganaHoltWintersConPronosticoExacto() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(estacional(36, 100, 0));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.HOLT_WINTERS);
        assertThat(r.wapeHw()).isEqualByComparingTo("0.0000");
        assertThat(r.errorWape()).isEqualByComparingTo("0.0000");
        assertThat(r.wapeSes().doubleValue()).isGreaterThan(0.05);
        assertThat(r.motivoSinHw()).isNull();
        assertThat(List.of(r.alfa(), r.beta(), r.gamma())).allSatisfy(p -> assertThat(p).isEqualByComparingTo("0.10"));
        assertThat(r.pronostico()).isEqualTo(bd("79.01", "69.14", "79.01"));
        assertThat(r.errorAbsolutoBacktest()).isCloseTo(0, within(1e-6));
        // reales del backtest: meses 31..36 = julio … diciembre → 100 × (1,0 + 0,9 + 0,95 + 1,0 + 1,3 + 1,6) × 12 / 12,15
        assertThat(r.realBacktest()).isCloseTo(100 * 6.75 * 12 / 12.15, within(1e-9));
    }

    /*
     * Serie constante 50, n = 36: SES y HW pronostican 50 en los 6 meses del backtest → WAPE 0 y 0 → empate → SES
     * (parsimonia). HW compitió: motivo null y su backtest queda como evidencia.
     */
    @Test
    void seleccionar_serieConstante_empateGanaSes() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(repetir(50, 36));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isNull();
        assertThat(r.wapeSes()).isEqualByComparingTo("0");
        assertThat(r.wapeHw()).isEqualByComparingTo("0");
        assertThat(r.backtestHw()).hasSize(6).allSatisfy(v -> assertThat(v).isEqualByComparingTo("50"));
        assertThat(r.pronostico()).isEqualTo(bd("50.00", "50.00", "50.00"));
        assertThat(r.beta()).isNull();
    }

    /* Un mes en 0 en una serie de 36 → HW no compite (MES_EN_CERO): SES con WAPE propio. */
    @Test
    void seleccionar_mesEnCero_soloSes() {
        double[] y = estacional(36, 100, 2);
        y[10] = 0;

        ResultadoPronostico r = SelectorPronostico.seleccionar(y);

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
        assertThat(r.wapeSes()).isNotNull();
        assertThat(r.errorWape()).isEqualTo(r.wapeSes());
        assertThat(r.wapeHw()).isNull();
        assertThat(r.backtestHw()).isEmpty();
    }

    /*
     * Nivel inicial no positivo (año 1 = 1, año 2 = 4, luego 4 × 6; n = 30):
     *   tend_0 = (4 − 1) / 12 = 0,25 ; nivel_0 = 1 − 6,5 × 0,25 = −0,625 → nivel_0 + tend_0 = −0,375 ≤ 0
     */
    @Test
    void seleccionar_nivelInicialNoPositivo_soloSes() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(unir(repetir(1, 12), repetir(4, 18)));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);
        assertThat(r.wapeHw()).isNull();
    }

    /*
     * 100 × 12, 200 × 12 y desplome a 0,001 durante 24 meses (n = 48, la serie de la prueba de A2): el inicio es
     * válido (solo usa los dos primeros años), pero en el entrenamiento del primer origen (42 meses, 18 en 0,001) las
     * 125 combinaciones llevan el nivel a ≤ 0 → HW no compite y queda NIVEL_NO_POSITIVO.
     */
    @Test
    void seleccionar_rejillaDescartadaEnElBacktest_nivelNoPositivo() {
        double[] y = unir(repetir(100, 12), repetir(200, 12), repetir(0.001, 24));
        assertThat(HoltWintersMultiplicativo.motivoSinHoltWinters(y)).isEmpty();
        assertThat(HoltWintersMultiplicativo.ajustar(Arrays.copyOf(y, 42)).motivo())
                .isEqualTo(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);

        ResultadoPronostico r = SelectorPronostico.seleccionar(y);

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);
        assertThat(r.wapeHw()).isNull();
        assertThat(r.backtestHw()).isEmpty();
        assertThat(r.wapeSes()).isNotNull();
    }

    // ------------------------------------------------------------------ casos borde

    /* Serie toda en 0 (n = 12 y n = 36): Σ y = 0 en el backtest → WAPE null → SES con pronóstico 0. */
    @Test
    void seleccionar_serieTodaEnCero_sesConPronosticoCeroYWapeNull() {
        ResultadoPronostico corta = SelectorPronostico.seleccionar(repetir(0, 12));
        ResultadoPronostico larga = SelectorPronostico.seleccionar(repetir(0, 36));

        for (ResultadoPronostico r : List.of(corta, larga)) {
            assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
            assertThat(r.pronostico()).isEqualTo(bd("0.00", "0.00", "0.00"));
            assertThat(r.wapeSes()).isNull();
            assertThat(r.errorWape()).isNull();
            assertThat(r.conBacktest()).isTrue();
            assertThat(r.realBacktest()).isZero();
        }
        assertThat(corta.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.POCA_HISTORIA);
        assertThat(larga.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
    }

    /* Demanda solo al principio (5 × 6, luego 0 × 6): los 6 meses evaluados suman 0 → WAPE null aunque haya historia. */
    @Test
    void seleccionar_backtestSinDemanda_wapeNull() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(unir(repetir(5, 6), repetir(0, 6)));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.wapeSes()).isNull();
        assertThat(r.errorAbsolutoBacktest()).isGreaterThan(0);
    }

    /* Horizonte configurable: HW con h = 1..13 repite el índice de enero en h = 13 (sin tendencia, 100 × S_1). */
    @Test
    void seleccionar_horizonteMayor_pronosticaLosMesesPedidos() {
        ResultadoPronostico r = SelectorPronostico.seleccionar(estacional(36, 100, 0), 13);

        assertThat(r.pronostico()).hasSize(13);
        assertThat(r.pronostico().get(12)).isEqualByComparingTo(r.pronostico().get(0));
        assertThat(SelectorPronostico.seleccionar(new double[] {1, 2, 3}, 1).pronostico()).hasSize(1);
    }

    @Test
    void seleccionar_entradasInvalidas_rechaza() {
        assertThatThrownBy(() -> SelectorPronostico.seleccionar(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SelectorPronostico.seleccionar(new double[] {1, 2, 3}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectorPronostico.seleccionar(new double[] {1, -2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectorPronostico.seleccionar(new double[] {-1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectorPronostico.seleccionar(new double[] {1, Double.NaN, 3, 4, 5, 6, 7, 8, 9}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultado_invariantes() {
        List<BigDecimal> tres = bd("1", "1", "1");
        assertThatThrownBy(() -> new ResultadoPronostico(MetodoPronostico.SES, 5, BigDecimal.ONE, BigDecimal.ONE,
                null, null, null, null, null, tres, List.of(), List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoPronostico(MetodoPronostico.HOLT_WINTERS, 36, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, null, null, null, MotivoSinHoltWinters.MES_EN_CERO, tres, List.of(),
                List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoPronostico(MetodoPronostico.SIN_HISTORIA, 1, null, null, null, null,
                null, null, null, tres, List.of(), List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
