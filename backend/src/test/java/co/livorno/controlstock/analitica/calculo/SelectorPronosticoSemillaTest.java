package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import co.livorno.controlstock.semilla.CatalogoSemilla;
import co.livorno.controlstock.semilla.ModeloDemanda;
import co.livorno.controlstock.semilla.ProductoSemilla;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Validación del método (ADR-010, plan de la fase 6, A3): con la demanda del modelo de la semilla
 * ({@code λ = base × factorMes × factorDía × 1,015^mes}, estacionalidad multiplicativa con tendencia) Holt-Winters
 * multiplicativo debe ganar el backtest a SES en un producto de cabeza. Es la evidencia de HW antes de la semilla de 36
 * meses (B12). Las pruebas pueden usar {@code semilla}: la regla ArchUnit de módulos analiza solo las clases de
 * producción ({@code ImportOption.DoNotIncludeTests}).
 *
 * Serie: 36 meses completos, octubre de 2023 a septiembre de 2026 (corte en octubre de 2026, como en la demo: el
 * backtest evalúa abril–septiembre de 2026). Tickets base 15 por día ({@code app.semilla.tickets-dia-base}).
 */
class SelectorPronosticoSemillaTest {

    private static final LocalDate INICIO = LocalDate.of(2023, 10, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final int MESES = 36;
    private static final double TICKETS_DIA_BASE = 15;
    private static final List<ProductoSemilla> CATALOGO = CatalogoSemilla.productos();

    private static ModeloDemanda modelo() {
        return new ModeloDemanda(CATALOGO, INICIO, FIN, TICKETS_DIA_BASE, new Random(42));
    }

    private static ProductoSemilla conRango(int rango) {
        return CATALOGO.stream().filter(p -> p.rangoDemanda() == rango).findFirst().orElseThrow();
    }

    /** λ_m = Σ de los días del mes de la demanda esperada del producto (unidades). */
    private static double[] demandaEsperadaMensual(ModeloDemanda modelo, ProductoSemilla producto) {
        double[] y = new double[MESES];
        YearMonth mes = YearMonth.from(INICIO);
        for (int m = 0; m < MESES; m++, mes = mes.plusMonths(1)) {
            y[m] = modelo.demandaEsperada(producto, mes.atDay(1), mes.lengthOfMonth());
        }
        return y;
    }

    /*
     * Producto de cabeza (rango 1: peso 16 % → ≈ 15 × 1,38 × 1,15 × 0,16 ≈ 3,8 unidades al día, ≈ 115 al mes) con la
     * demanda esperada exacta: estacionalidad × 1,015^mes. SES va un paso atrás de la estación; HW la modela.
     * Resultado (abril–septiembre de 2026, Σ y = 1.097,06): WAPE SES 0,1164 (pronostica 157,33 y 173,17 planos) vs
     * WAPE HW 0,0232 (Σ|e| = 25,49) → HOLT_WINTERS con (α, β, γ) = (0,1; 0,5; 0,9).
     */
    @Test
    void productoDeCabeza_demandaEsperada_ganaHoltWinters() {
        ModeloDemanda modelo = modelo();
        ProductoSemilla cabeza = conRango(1);
        assertThat(cabeza.estancado()).isFalse();

        ResultadoPronostico r = SelectorPronostico.seleccionar(demandaEsperadaMensual(modelo, cabeza));

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.HOLT_WINTERS);
        assertThat(r.motivoSinHw()).isNull();
        assertThat(r.wapeHw()).isLessThan(r.wapeSes());
        assertThat(r.wapeHw()).isEqualByComparingTo("0.0232");
        assertThat(r.wapeSes()).isEqualByComparingTo("0.1164");
    }

    /*
     * Mismo producto con ruido Poisson: y_m ~ Poisson(λ_m) con Random(42) (la suma de las Poisson diarias es Poisson
     * con la suma de las λ). Con ≈ 115 unidades al mes el ruido es ≈ ±11 (≈ 9 %): HW sigue ganando.
     * Resultado (Σ y = 1.065): WAPE SES 0,1206 vs WAPE HW 0,0520 (Σ|e| = 55,40) → HOLT_WINTERS con (0,1; 0,1; 0,3).
     */
    @Test
    void productoDeCabeza_conRuidoPoisson_ganaHoltWinters() {
        ModeloDemanda modelo = modelo();
        double[] esperada = demandaEsperadaMensual(modelo, conRango(1));
        Random random = new Random(42);
        double[] y = new double[MESES];
        for (int m = 0; m < MESES; m++) {
            y[m] = ModeloDemanda.poisson(esperada[m], random);
        }

        ResultadoPronostico r = SelectorPronostico.seleccionar(y);

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.HOLT_WINTERS);
        assertThat(r.wapeHw()).isLessThan(r.wapeSes());
        assertThat(r.wapeHw()).isEqualByComparingTo("0.0520");
        assertThat(r.wapeSes()).isEqualByComparingTo("0.1206");
    }

    /*
     * Producto de cola estancado (LIV004, rango 20): sin ventas en los últimos 90 días de la historia → los meses de
     * julio a septiembre de 2026 quedan en 0 (o casi) y al menos agosto y septiembre son 0 → MES_EN_CERO → SES.
     */
    @Test
    void productoDeColaEstancado_mesesEnCero_caeASes() {
        ModeloDemanda modelo = modelo();
        ProductoSemilla estancado = CATALOGO.stream().filter(ProductoSemilla::estancado).findFirst().orElseThrow();

        double[] y = demandaEsperadaMensual(modelo, estancado);
        assertThat(y[MESES - 1]).isZero();

        ResultadoPronostico r = SelectorPronostico.seleccionar(y);

        assertThat(r.metodo()).isEqualTo(MetodoPronostico.SES);
        assertThat(r.motivoSinHw()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
        assertThat(r.wapeHw()).isNull();
    }
}
