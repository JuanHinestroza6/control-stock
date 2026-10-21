package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.util.Arrays;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class HoltWintersMultiplicativoTest {

    private static final Offset<Double> EPS = within(1e-9);
    private static final double[] REJILLA = {0.1, 0.3, 0.5, 0.7, 0.9};

    /**
     * Factores de mes de la semilla (ModeloDemanda, ene … dic), normalizados a media 1: S_i = f_i × 12 / 12,15.
     * Σ f = 0,8 + 0,7 + 0,8 + 0,85 + 1,0 + 1,25 + 1,0 + 0,9 + 0,95 + 1,0 + 1,3 + 1,6 = 12,15.
     */
    private static final double[] S = normalizar(
            new double[] {0.8, 0.7, 0.8, 0.85, 1.0, 1.25, 1.0, 0.9, 0.95, 1.0, 1.3, 1.6});

    private static double[] normalizar(double[] f) {
        double suma = Arrays.stream(f).sum();
        return Arrays.stream(f).map(x -> x * f.length / suma).toArray();
    }

    /** y_t = (base + pendiente·t) × S_t, t = 1..n (t = 1 es enero). */
    private static double[] serie(int n, double base, double pendiente) {
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

    /*
     * Inicialización a mano con 24 meses:
     *   año 1 = 10 × 11 meses + diciembre 16  → ȳ1 = 126 / 12 = 10,5
     *   año 2 = 11 × 11 meses + diciembre 17,6 → ȳ2 = 138,6 / 12 = 11,55
     *   tend_0  = (11,55 − 10,5) / 12 = 0,0875
     *   nivel_0 = 10,5 − 6,5 × 0,0875 = 10,5 − 0,56875 = 9,93125
     *   r_1  = 10 / (9,93125 + 1 × 0,0875)  = 10 / 10,01875 = 0,998129
     *   r_12 = 16 / (9,93125 + 12 × 0,0875) = 16 / 10,98125 = 1,457029
     *   Σ r_j = 11,984429 → s_1 = 0,998129 × 12 / 11,984429 = 0,999425 ; s_12 = 1,458922 ; media de s = 1
     */
    @Test
    void inicializar_ejemploAMano_indicesComoRazonesDeMediaUno() {
        double[] y = unir(repetir(10, 11), new double[] {16}, repetir(11, 11), new double[] {17.6});

        InicializacionHoltWinters inicio = HoltWintersMultiplicativo.inicializar(y);

        assertThat(inicio.tendencia()).isCloseTo(0.0875, EPS);
        assertThat(inicio.nivel()).isCloseTo(9.93125, EPS);
        double[] r = inicio.razones();
        assertThat(r[0]).isCloseTo(10 / 10.01875, EPS);
        assertThat(r[11]).isCloseTo(16 / 10.98125, EPS);
        assertThat(Arrays.stream(r).sum()).isCloseTo(11.984429, within(1e-6));
        double[] s = inicio.indices();
        assertThat(s[0]).isCloseTo(0.999425, within(1e-6));
        assertThat(s[11]).isCloseTo(1.458922, within(1e-6));
        assertThat(Arrays.stream(s).average().orElseThrow()).isCloseTo(1.0, EPS);
    }

    /*
     * y_t = 100 × S_t (sin tendencia, S de media 1): ȳ1 = ȳ2 = 100 → tend_0 = 0, nivel_0 = 100, r_i = S_i, s_i = S_i.
     * En cada t: ŷ_t = 100 × S_t = y_t (error 0); nivel_t = α·100 + (1 − α)·100 = 100; tend_t = 0;
     * s_t = γ·S_t + (1 − γ)·S_t = S_t. El pronóstico es exacto para cualquier (α, β, γ).
     */
    @Test
    void ajustar_serieEstacionalPura_pronosticoExactoConCualquierParametro() {
        double[] y = serie(24, 100, 0);

        InicializacionHoltWinters inicio = HoltWintersMultiplicativo.inicializar(y);
        assertThat(inicio.tendencia()).isCloseTo(0.0, EPS);
        assertThat(inicio.nivel()).isCloseTo(100.0, EPS);
        assertThat(inicio.indices()).containsExactly(S, EPS);

        double[] esperado = Arrays.stream(S).map(x -> 100 * x).toArray();   // meses 25..36 = ene..dic
        for (double alfa : REJILLA) {
            for (double beta : REJILLA) {
                for (double gamma : REJILLA) {
                    AjusteHoltWinters ajuste = HoltWintersMultiplicativo.ajustar(y, alfa, beta, gamma).orElseThrow();
                    assertThat(ajuste.pronosticar(12)).containsExactly(esperado, EPS);
                    assertThat(ajuste.sse()).isLessThan(1e-9);
                }
            }
        }
        // Todas empatan con SSE ≈ 0 → orden lexicográfico: (0,1; 0,1; 0,1).
        ResultadoHoltWinters resultado = HoltWintersMultiplicativo.ajustar(y);
        assertThat(resultado.elegible()).isTrue();
        assertThat(resultado.ajuste().alfa()).isEqualTo(0.1);
        assertThat(resultado.ajuste().beta()).isEqualTo(0.1);
        assertThat(resultado.ajuste().gamma()).isEqualTo(0.1);
        assertThat(resultado.ajuste().pronosticar(3)).containsExactly(new double[] {80 * 12 / 12.15,
                70 * 12 / 12.15, 80 * 12 / 12.15}, EPS);
    }

    /*
     * y_t = (100 + 2t) × S_t: la media del año mezcla tendencia y estación, así que la inicialización no es exacta.
     * Se exige WAPE = Σ|y − ŷ| / Σ y < 2 % a 3 meses:
     *   n = 33 (ajuste hasta septiembre del año 3) → octubre, noviembre, diciembre (pico): WAPE ≈ 0,02 %
     *   n = 36 (ajuste hasta diciembre del año 3)  → enero, febrero, marzo:              WAPE ≈ 0,22 %
     */
    @Test
    void ajustar_serieConTendencia_wapeMenorADosPorCiento() {
        for (int n : new int[] {33, 36}) {
            double[] completa = serie(n + 3, 100, 2);
            double[] entrenamiento = Arrays.copyOf(completa, n);

            ResultadoHoltWinters resultado = HoltWintersMultiplicativo.ajustar(entrenamiento);
            double[] pronostico = resultado.ajuste().pronosticar(3);

            double errores = 0;
            double reales = 0;
            for (int h = 0; h < 3; h++) {
                errores += Math.abs(completa[n + h] - pronostico[h]);
                reales += completa[n + h];
            }
            assertThat(errores / reales).as("WAPE con n = %d", n).isLessThan(0.02);
        }
    }

    /*
     * Combinación descartada: año 1 = 100, año 2 = 200 y luego 6 meses de 1 (la demanda se desploma).
     * Con (α, β, γ) = (0,9; 0,9; 0,1), al final del mes 24 nivel ≈ 320,13 y tend ≈ 18,29:
     *   mes 25: nivel = 0,9 × (1 / 1,6767) + 0,1 × 338,42 = 0,54 + 33,84 = 34,38
     *           tend  = 0,9 × (34,38 − 320,13) + 0,1 × 18,29 = −255,35
     *   mes 26: nivel = 0,9 × (1 / 1,4621) + 0,1 × (34,38 − 255,35) = 0,62 − 22,10 = −21,48 ≤ 0 → descartada
     * Con (0,1; 0,1; 0,1) el nivel baja despacio y sigue positivo. De las 125 se descartan 95 y el mejor SSE entre las
     * restantes es (0,3; 0,1; 0,9).
     */
    @Test
    void ajustar_combinacionQueLlevaElNivelACero_seDescarta() {
        double[] y = unir(repetir(100, 12), repetir(200, 12), repetir(1, 6));

        assertThat(HoltWintersMultiplicativo.ajustar(y, 0.9, 0.9, 0.1)).isEmpty();
        Optional<AjusteHoltWinters> lento = HoltWintersMultiplicativo.ajustar(y, 0.1, 0.1, 0.1);
        assertThat(lento).isPresent();
        assertThat(lento.get().nivel()).isPositive();

        int descartadas = 0;
        double menorSse = Double.MAX_VALUE;
        for (double alfa : REJILLA) {
            for (double beta : REJILLA) {
                for (double gamma : REJILLA) {
                    Optional<AjusteHoltWinters> ajuste = HoltWintersMultiplicativo.ajustar(y, alfa, beta, gamma);
                    if (ajuste.isEmpty()) {
                        descartadas++;
                    } else {
                        menorSse = Math.min(menorSse, ajuste.get().sse());
                    }
                }
            }
        }
        assertThat(descartadas).isEqualTo(95);

        AjusteHoltWinters mejor = HoltWintersMultiplicativo.ajustar(y).ajuste();
        assertThat(new double[] {mejor.alfa(), mejor.beta(), mejor.gamma()}).containsExactly(0.3, 0.1, 0.9);
        assertThat(mejor.sse()).isEqualTo(menorSse);
    }

    /* Desplome a 0,001 durante 24 meses tras 100 / 200: las 125 combinaciones llevan el nivel a ≤ 0 → NIVEL_NO_POSITIVO. */
    @Test
    void ajustar_todasLasCombinacionesDescartadas_nivelNoPositivo() {
        double[] y = unir(repetir(100, 12), repetir(200, 12), repetir(0.001, 24));

        assertThat(HoltWintersMultiplicativo.motivoSinHoltWinters(y)).isEmpty();   // el inicio sí es válido
        ResultadoHoltWinters resultado = HoltWintersMultiplicativo.ajustar(y);

        assertThat(resultado.elegible()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);
    }

    /* n = 23 (menos de dos temporadas) → POCA_HISTORIA, aunque además tenga un mes en 0 (se evalúa primero). */
    @Test
    void ajustar_menosDeVeinticuatroMeses_pocaHistoria() {
        assertThat(HoltWintersMultiplicativo.ajustar(serie(23, 100, 0)).motivo())
                .isEqualTo(MotivoSinHoltWinters.POCA_HISTORIA);
        double[] conCero = serie(23, 100, 0);
        conCero[5] = 0;
        assertThat(HoltWintersMultiplicativo.motivoSinHoltWinters(conCero))
                .contains(MotivoSinHoltWinters.POCA_HISTORIA);
        assertThat(HoltWintersMultiplicativo.motivoSinHoltWinters(serie(24, 100, 0))).isEmpty();
    }

    /* Un solo mes en 0 en cualquier posición de la serie (aquí el 30 de 36) → MES_EN_CERO. */
    @Test
    void ajustar_mesEnCero_mesEnCero() {
        double[] y = serie(36, 100, 2);
        y[29] = 0;

        ResultadoHoltWinters resultado = HoltWintersMultiplicativo.ajustar(y);

        assertThat(resultado.elegible()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
    }

    /*
     * Nivel inicial no positivo: año 1 = 1, año 2 = 4 (crecimiento muy fuerte).
     *   tend_0 = (4 − 1) / 12 = 0,25 ; nivel_0 = 1 − 6,5 × 0,25 = −0,625
     *   nivel_0 + 1 × tend_0 = −0,375 ≤ 0 → NIVEL_NO_POSITIVO (la razón r_1 no tendría sentido)
     */
    @Test
    void ajustar_nivelInicialNoPositivo_nivelNoPositivo() {
        double[] y = unir(repetir(1, 12), repetir(4, 12));

        assertThat(HoltWintersMultiplicativo.ajustar(y).motivo()).isEqualTo(MotivoSinHoltWinters.NIVEL_NO_POSITIVO);
        assertThatThrownBy(() -> HoltWintersMultiplicativo.inicializar(y))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NIVEL_NO_POSITIVO");
    }

    /*
     * Pronóstico con recorte a 0 y vuelta del índice estacional: nivel 10, tend −4, s = 1, 2, …, 12:
     *   h = 1 → (10 − 4) × 1 = 6 ; h = 2 → (10 − 8) × 2 = 4 ; h = 3 → (10 − 12) × 3 = −6 → 0
     * Con tend 0, h = 13 vuelve a s_{n−11} (posición 0): 10 × 1 = 10.
     */
    @Test
    void pronosticar_recortaACeroYRepiteLaEstacion() {
        double[] indices = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        assertThat(new AjusteHoltWinters(0.1, 0.1, 0.1, 10, -4, indices, 0).pronosticar(3))
                .containsExactly(new double[] {6, 4, 0}, EPS);
        double[] trece = new AjusteHoltWinters(0.1, 0.1, 0.1, 10, 0, indices, 0).pronosticar(13);
        assertThat(trece[12]).isCloseTo(10.0, EPS);
        assertThat(trece[11]).isCloseTo(120.0, EPS);
    }

    @Test
    void entradasInvalidas_rechaza() {
        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(new double[0]))
                .isInstanceOf(IllegalArgumentException.class);
        double[] negativa = serie(24, 100, 0);
        negativa[3] = -1;
        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(negativa))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(serie(24, 100, 0), 0.0, 0.1, 0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(serie(23, 100, 0), 0.1, 0.1, 0.1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("POCA_HISTORIA");
        assertThatThrownBy(() -> new AjusteHoltWinters(0.1, 0.1, 0.1, 1, 0, new double[11], 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultadoHoltWinters(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /* h cruza más de un ciclo: nivel 10, tend 1, s = 1..12 → h = 25 usa s posición 0: (10 + 25) × 1 = 35; h = 26 → 36 × 2. */
    @Test
    void pronosticar_horizonteConDosCiclos_repiteIndicesYSumaTendencia() {
        double[] indices = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        double[] p = new AjusteHoltWinters(0.1, 0.1, 0.1, 10, 1, indices, 0).pronosticar(26);

        assertThat(p).hasSize(26);
        assertThat(p[12]).isCloseTo(23 * 1, EPS);    // h = 13
        assertThat(p[24]).isCloseTo(35 * 1, EPS);    // h = 25
        assertThat(p[25]).isCloseTo(36 * 2, EPS);    // h = 26
    }

    @Test
    void pronosticar_horizonteCeroONegativo_vacioOExcepcion() {
        AjusteHoltWinters ajuste = new AjusteHoltWinters(0.1, 0.1, 0.1, 10, 0, new double[12], 0);

        assertThat(ajuste.pronosticar(0)).isEmpty();
        assertThatThrownBy(() -> ajuste.pronosticar(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    /* Con una serie con tendencia la inicialización sigue dando 12 índices positivos de media 1. */
    @Test
    void inicializar_serieConTendencia_indicesPositivosDeMediaUno() {
        double[] s = HoltWintersMultiplicativo.inicializar(serie(24, 100, 2)).indices();

        assertThat(s).hasSize(12);
        assertThat(Arrays.stream(s).min().orElseThrow()).isPositive();
        assertThat(Arrays.stream(s).average().orElseThrow()).isCloseTo(1.0, EPS);
    }

    /* Los parámetros deben estar en (0, 1]: 1 es válido; 0, > 1, negativo y NaN se rechazan (cada uno de α, β, γ). */
    @Test
    void ajustar_parametrosFueraDeRango_rechazaYUnoEsValido() {
        double[] y = serie(24, 100, 0);

        assertThat(HoltWintersMultiplicativo.ajustar(y, 1.0, 1.0, 1.0)).isPresent();
        for (double malo : new double[] {0.0, -0.1, 1.0001, Double.NaN}) {
            assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(y, malo, 0.5, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(y, 0.5, malo, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(y, 0.5, 0.5, malo))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* Serie con mes en 0 y parámetros fijos: no es elegible → excepción con el motivo. */
    @Test
    void ajustar_parametrosFijosConMesEnCero_lanzaConMotivo() {
        double[] y = serie(24, 100, 0);
        y[7] = 0;

        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(y, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MES_EN_CERO");
    }

    /* Valores no finitos y null en HW. */
    @Test
    void ajustar_serieNoFinitaONula_rechaza() {
        double[] nan = serie(24, 100, 0);
        nan[0] = Double.NaN;
        double[] inf = serie(24, 100, 0);
        inf[10] = Double.POSITIVE_INFINITY;

        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(nan)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HoltWintersMultiplicativo.motivoSinHoltWinters(inf))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HoltWintersMultiplicativo.ajustar(null)).isInstanceOf(NullPointerException.class);
    }

    /* Exactamente n = 24 con tendencia: elegible y ajusta; los 12 índices finales son positivos. */
    @Test
    void ajustar_exactamenteVeinticuatroMeses_esElegibleYDevuelveAjuste() {
        ResultadoHoltWinters r = HoltWintersMultiplicativo.ajustar(serie(24, 100, 2));

        assertThat(r.elegible()).isTrue();
        assertThat(r.motivo()).isNull();
        assertThat(r.ajuste().estacionales()).hasSize(12);
        assertThat(Arrays.stream(r.ajuste().estacionales()).min().orElseThrow()).isPositive();
        assertThat(r.ajuste().nivel()).isPositive();
    }

    /* Un ajuste con tendencia negativa fuerte recorta a 0 y nunca devuelve valores negativos. */
    @Test
    void ajustar_serieDecreciente_pronosticoNuncaNegativo() {
        double[] y = serie(36, 400, -9);

        double[] p = HoltWintersMultiplicativo.ajustar(y).ajuste().pronosticar(36);

        assertThat(Arrays.stream(p).min().orElseThrow()).isGreaterThanOrEqualTo(0.0);
    }
}
