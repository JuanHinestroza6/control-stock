package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Casos borde adicionales del selector: fronteras de n, horizonte, escalas, mes en cero y no mutación. */
class SelectorPronosticoBordesTest {

    private static final double[] F = {0.8, 0.7, 0.8, 0.85, 1.0, 1.25, 1.0, 0.9, 0.95, 1.0, 1.3, 1.6};

    private static double[] estacional(int n) {
        double suma = Arrays.stream(F).sum();
        double[] y = new double[n];
        for (int t = 0; t < n; t++) {
            y[t] = (100 + 2 * (t + 1)) * F[t % 12] * 12 / suma;
        }
        return y;
    }

    private static double[] repetir(double v, int n) {
        double[] y = new double[n];
        Arrays.fill(y, v);
        return y;
    }

    @Test
    void seleccionar_fronteraOchoNueve_backtestSoloDesdeNueve() {
        double[] base = {10, 10, 10, 10, 10, 10, 20, 20, 20};

        assertThat(SelectorPronostico.seleccionar(Arrays.copyOf(base, 8)).conBacktest()).isFalse();
        assertThat(SelectorPronostico.seleccionar(Arrays.copyOf(base, 8)).errorWape()).isNull();
        assertThat(SelectorPronostico.seleccionar(base).conBacktest()).isTrue();
        assertThat(SelectorPronostico.seleccionar(base).errorWape()).isNotNull();
    }

    @Test
    void seleccionar_fronteraDosTres_sinHistoriaSoloConMenosDeTres() {
        assertThat(SelectorPronostico.seleccionar(new double[] {5, 7}).metodo()).isEqualTo(MetodoPronostico.SIN_HISTORIA);
        assertThat(SelectorPronostico.seleccionar(new double[] {5, 7, 6}).metodo()).isEqualTo(MetodoPronostico.SES);
    }

    @Test
    void seleccionar_horizonteUnoYMayorADoce_tamanoYValoresCoherentes() {
        double[] y = estacional(36);

        ResultadoPronostico uno = SelectorPronostico.seleccionar(y, 1);
        ResultadoPronostico tres = SelectorPronostico.seleccionar(y);
        ResultadoPronostico largo = SelectorPronostico.seleccionar(y, 15);
        ResultadoPronostico sesLargo = SelectorPronostico.seleccionar(repetir(50, 10), 14);

        assertThat(uno.pronostico()).hasSize(1);
        assertThat(uno.pronostico().get(0)).isEqualByComparingTo(tres.pronostico().get(0));
        assertThat(largo.pronostico()).hasSize(15);
        assertThat(largo.pronostico().subList(0, 3)).isEqualTo(tres.pronostico());
        assertThat(sesLargo.pronostico()).hasSize(14).allSatisfy(v -> assertThat(v).isEqualByComparingTo("50"));
    }

    @Test
    void seleccionar_escalas_dosEnCantidadesYParametrosCuatroEnWape() {
        for (double[] serie : new double[][] {estacional(36), estacional(36), estacional(12), {3, 7, 4}}) {
            ResultadoPronostico r = SelectorPronostico.seleccionar(serie);

            List<BigDecimal> dos = new java.util.ArrayList<>(r.pronostico());
            dos.addAll(r.backtestSes());
            dos.addAll(r.backtestHw());
            dos.add(r.alfa());
            if (r.beta() != null) {
                dos.add(r.beta());
                dos.add(r.gamma());
            }
            assertThat(dos).allSatisfy(v -> assertThat(v.scale()).isEqualTo(2));
            for (BigDecimal w : new BigDecimal[] {r.wapeSes(), r.wapeHw(), r.errorWape()}) {
                if (w != null) {
                    assertThat(w.scale()).isEqualTo(4);
                }
            }
        }
    }

    @Test
    void seleccionar_redondeo_coincideConHalfUpDelValorSinRedondear() {
        double[] serie = {3, 7, 4, 9};

        ResultadoPronostico r = SelectorPronostico.seleccionar(serie);

        AjusteSes ses = SuavizadoExponencial.ajustar(serie);
        double[] crudo = ses.pronosticar(3);
        for (int i = 0; i < 3; i++) {
            assertThat(r.pronostico().get(i))
                    .isEqualTo(BigDecimal.valueOf(crudo[i]).setScale(2, RoundingMode.HALF_UP));
        }
        assertThat(r.alfa()).isEqualTo(BigDecimal.valueOf(ses.alfa()).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void seleccionar_mesEnCeroSoloEnEntrenamientoOSoloEnPrueba_ambosSinHoltWinters() {
        double[] alInicio = estacional(36);
        alInicio[0] = 0;
        double[] alFinal = estacional(36);
        alFinal[35] = 0;

        for (double[] serie : new double[][] {alInicio, alFinal}) {
            ResultadoPronostico r = SelectorPronostico.seleccionar(serie);

            assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
            assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
            assertThat(r.wapeHw()).isNull();
            assertThat(r.backtestHw()).isEmpty();
            assertThat(r.wapeSes()).isNotNull();
        }
    }

    @Test
    void seleccionar_ceroEnLaParteDePruebaPeroNoTodaLaPrueba_wapeSesConError() {
        double[] serie = repetir(10, 12);
        serie[11] = 0;

        ResultadoPronostico r = SelectorPronostico.seleccionar(serie);

        assertThat(r.wapeSes()).isNotNull();
        assertThat(r.realBacktest()).isEqualTo(50.0);
        assertThat(r.errorAbsolutoBacktest()).isGreaterThan(0);
        assertThat(r.pronostico()).allSatisfy(v -> assertThat(v.signum()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void seleccionar_noMutaLaSerieDeEntrada() {
        double[] y = estacional(36);
        double[] copia = y.clone();

        SelectorPronostico.seleccionar(y);

        assertThat(y).containsExactly(copia);
    }

    @Test
    void seleccionar_mismaEntrada_resultadoDeterminista() {
        assertThat(SelectorPronostico.seleccionar(estacional(36)))
                .isEqualTo(SelectorPronostico.seleccionar(estacional(36)));
    }
}
