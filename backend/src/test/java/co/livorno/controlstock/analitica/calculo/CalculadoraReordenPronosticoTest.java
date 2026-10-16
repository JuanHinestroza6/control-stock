package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ROP con demanda pronosticada (ADR-010, decisión 3): sobrecarga de {@link CalculadoraReorden}. */
class CalculadoraReordenPronosticoTest {

    private final CalculadoraReorden calculadora =
            new CalculadoraReorden(CalculadoraReordenTest.propiedadesPorDefecto());

    private static final LocalDate CORTE_25_OCT = LocalDate.of(2026, 10, 25);

    /** Pronóstico de la variante: octubre 31 (1/día), noviembre 60 (2/día), diciembre 93 (3/día). */
    private static PronosticoMensual pronostico(String oct, String nov, String dic) {
        return new PronosticoMensual(YearMonth.of(2026, 10),
                List.of(new BigDecimal(oct), new BigDecimal(nov), new BigDecimal(dic)));
    }

    private static final PronosticoMensual OCT_NOV_DIC = pronostico("31.00", "60.00", "93.00");

    /*
     * Serie observada de 76 días con d̄ = 2 y s = 1,2 exactos:
     *   2 días en 1, 2 en 3, 13 en 0, 13 en 4 y 46 en 2 → Σ = 2 + 6 + 0 + 52 + 92 = 152 → d̄ = 152/76 = 2.
     *   Σ (x − 2)² = 2·1 + 2·1 + 13·4 + 13·4 = 108 → s² = 108/75 = 1,44 → s = 1,2.
     */
    private static SerieDemanda serieS12() {
        int[] v = new int[76];
        Arrays.fill(v, 0, 2, 1);
        Arrays.fill(v, 2, 4, 3);
        Arrays.fill(v, 4, 17, 0);
        Arrays.fill(v, 17, 30, 4);
        Arrays.fill(v, 30, 76, 2);
        return SerieDemanda.de(v);
    }

    /*
     * Ejemplo del plan (ROP 31). Corte 25-oct, L = 15, clase A (z 1,65):
     *   μL = 7 días × 1 (25..31 oct) + 8 días × 2 (1..8 nov) = 23 ≥ 5 → NORMAL
     *   SS = 1,65 × 1,2 × √15 = 1,98 × 3,8730 = 7,67 → ROP = ceil(23 + 7,67) = ceil(30,67) = 31.
     *   Tasa media del lead time = 23 / 15 = 1,5333/día.
     * Con la fórmula histórica la misma serie daría μL = 2 × 15 = 30 → ceil(37,67) = 38 (fuente HISTORICA).
     */
    @Test
    void calcular_ejemploDelPlan_rop31() {
        ReordenConFuente r = calculadora.calcular(serieS12(), 15, 'A', OCT_NOV_DIC, CORTE_25_OCT);

        assertThat(r.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(r.reorden().metodo()).isEqualTo(MetodoReorden.NORMAL);
        assertThat(r.reorden().puntoReorden()).isEqualTo(31);
        assertThat(r.reorden().stockSeguridad().toPlainString()).isEqualTo("7.67");
        assertThat(r.reorden().desviacion().toPlainString()).isEqualTo("1.2000");
        assertThat(r.reorden().demandaMedia().toPlainString()).isEqualTo("2.0000");
        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("23.00");
        assertThat(r.tasaPronosticada().toPlainString()).isEqualTo("1.5333");

        ReordenConFuente historico = calculadora.calcular(serieS12(), 15, 'A', null, CORTE_25_OCT);
        assertThat(historico.fuente()).isEqualTo(FuenteDemanda.HISTORICA);
        assertThat(historico.reorden().puntoReorden()).isEqualTo(38);
        assertThat(historico.demandaLeadTime().toPlainString()).isEqualTo("30.00");
        assertThat(historico.tasaPronosticada()).isNull();
    }

    /*
     * Horizonte agotado dentro del lead time. Corte 20-dic, L = 20, clase B (z 1,28):
     *   μL = 12 × 3 (20..31 dic) + 8 × 3 (1..8 ene, tasa del último mes) = 60
     *   SS = 1,28 × 1,2 × √20 = 1,536 × 4,4721 = 6,87 → ROP = ceil(66,87) = 67.
     */
    @Test
    void calcular_horizonteAgotado_usaUltimaTasa() {
        ReordenConFuente r = calculadora.calcular(serieS12(), 20, 'B', OCT_NOV_DIC, LocalDate.of(2026, 12, 20));

        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("60.00");
        assertThat(r.tasaPronosticada().toPlainString()).isEqualTo("3.0000");
        assertThat(r.reorden().stockSeguridad().toPlainString()).isEqualTo("6.87");
        assertThat(r.reorden().puntoReorden()).isEqualTo(67);
    }

    /*
     * POISSON con pronóstico. Octubre 3,10 (0,1/día), noviembre 3,00 (0,1/día); corte 25-oct, L = 15 → μL = 1,5 < 5.
     *   Clase A (p 0,95): P(≤0) = 0,2231 ; P(≤1) = 0,5578 ; P(≤2) = 0,8088 ; P(≤3) = 0,9344 ; P(≤4) = 0,9814 → ROP 4.
     *   SS = 4 − 1,5 = 2,50 (informativo).
     */
    @Test
    void calcular_demandaBaja_poisson() {
        ReordenConFuente r = calculadora.calcular(serie(40, 3), 15, 'A', pronostico("3.10", "3.00", "3.10"),
                CORTE_25_OCT);

        assertThat(r.reorden().metodo()).isEqualTo(MetodoReorden.POISSON);
        assertThat(r.reorden().puntoReorden()).isEqualTo(4);
        assertThat(r.reorden().stockSeguridad().toPlainString()).isEqualTo("2.50");
        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("1.50");
    }

    /* Pronóstico 0 en el lead time → SIN_DEMANDA (ROP 0) aunque la serie observada tenga ventas. */
    @Test
    void calcular_pronosticoCero_sinDemanda() {
        ReordenConFuente r = calculadora.calcular(serieS12(), 15, 'A', pronostico("0.00", "0.00", "0.00"),
                CORTE_25_OCT);

        assertThat(r.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(r.reorden().metodo()).isEqualTo(MetodoReorden.SIN_DEMANDA);
        assertThat(r.reorden().puntoReorden()).isZero();
        assertThat(r.reorden().stockSeguridad().toPlainString()).isEqualTo("0.00");
        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("0.00");
        assertThat(r.tasaPronosticada().toPlainString()).isEqualTo("0.0000");
    }

    /*
     * Serie observada toda en 0 (40 días) pero pronóstico positivo (la variante vendió en los 180 días de la mezcla):
     * manda el pronóstico. μL = 23, s = 0 → SS = 0 → ROP = 23 (NORMAL).
     */
    @Test
    void calcular_serieObservadaEnCero_peroPronosticoPositivo_usaPronostico() {
        ReordenConFuente r = calculadora.calcular(SerieDemanda.de(new int[40]), 15, 'C', OCT_NOV_DIC, CORTE_25_OCT);

        assertThat(r.reorden().metodo()).isEqualTo(MetodoReorden.NORMAL);
        assertThat(r.reorden().puntoReorden()).isEqualTo(23);
    }

    /* n = 20 < 30 → SIN_HISTORIA con ambas fuentes; con pronóstico se informa μL aunque el ROP sea null. */
    @Test
    void calcular_historiaCorta_sinHistoria() {
        ReordenConFuente conPronostico = calculadora.calcular(serie(20, 40), 15, 'A', OCT_NOV_DIC, CORTE_25_OCT);
        ReordenConFuente sinPronostico = calculadora.calcular(serie(20, 40), 15, 'A', null, CORTE_25_OCT);

        assertThat(conPronostico.reorden().metodo()).isEqualTo(MetodoReorden.SIN_HISTORIA);
        assertThat(conPronostico.reorden().puntoReorden()).isNull();
        assertThat(conPronostico.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(conPronostico.demandaLeadTime().toPlainString()).isEqualTo("23.00");
        assertThat(sinPronostico.reorden().metodo()).isEqualTo(MetodoReorden.SIN_HISTORIA);
        assertThat(sinPronostico.fuente()).isEqualTo(FuenteDemanda.HISTORICA);
        assertThat(sinPronostico.demandaLeadTime()).isNull();
    }

    /* Sin pronóstico, la sobrecarga devuelve exactamente el resultado de la fase 5 (contrato intacto). */
    @Test
    void calcular_sinPronostico_igualALaFormulaHistorica() {
        SerieDemanda s = serie(50, 1);

        ReordenConFuente r = calculadora.calcular(s, 23, 'C', null, CORTE_25_OCT);

        assertThat(r.reorden()).isEqualTo(calculadora.calcular(s, 23, 'C'));
        assertThat(r.reorden().puntoReorden()).isEqualTo(1);
        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("0.46");
    }

    @Test
    void calcular_leadTimeNegativo_falla() {
        assertThatThrownBy(() -> calculadora.calcular(serieS12(), -1, 'A', OCT_NOV_DIC, CORTE_25_OCT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** n días con {@code total} unidades vendidas de a una en los primeros días y el resto en 0. */
    private static SerieDemanda serie(int n, int total) {
        int[] valores = new int[n];
        for (int i = 0; i < total; i++) {
            valores[i % n]++;
        }
        return SerieDemanda.de(valores);
    }

    /* L = 0: μL = 0 pero la tasa informada es la del día del corte (1/día); no es SIN_DEMANDA si la tasa es > 0. */
    @Test
    void calcular_leadTimeCero_usaTasaDelDiaDelCorte() {
        ReordenConFuente r = calculadora.calcular(serieS12(), 0, 'A', OCT_NOV_DIC, CORTE_25_OCT);

        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("0.00");
        assertThat(r.tasaPronosticada().toPlainString()).isEqualTo("1.0000");
        assertThat(r.fuente()).isEqualTo(FuenteDemanda.PRONOSTICO);
    }

    /* L cruza el fin de año: dic 31 (1/día), ene 62 (2/día); corte 28-dic, L = 10 → μL = 16 ; SS = 1,65·1,2·√10 = 6,26 → 23. */
    @Test
    void calcular_leadTimeCruzaFinDeAnio_rop23() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2026, 12),
                List.of(new BigDecimal("31.00"), new BigDecimal("62.00")));

        ReordenConFuente r = calculadora.calcular(serieS12(), 10, 'A', p, LocalDate.of(2026, 12, 28));

        assertThat(r.demandaLeadTime().toPlainString()).isEqualTo("16.00");
        assertThat(r.reorden().puntoReorden()).isEqualTo(23);
    }

    /* Horizonte de un solo mes. Noviembre 30 (1/día), corte 20-nov, L = 20 → μL = 20 ; SS(B) = 1,28·1,2·√20 = 6,87 → 27. */
    @Test
    void calcular_horizonteDeUnMes_rop27() {
        PronosticoMensual p = new PronosticoMensual(YearMonth.of(2026, 11), List.of(new BigDecimal("30.00")));

        ReordenConFuente r = calculadora.calcular(serieS12(), 20, 'B', p, LocalDate.of(2026, 11, 20));

        assertThat(r.reorden().puntoReorden()).isEqualTo(27);
    }

    /* La ruta de 3 argumentos y la sobrecarga con pronóstico null coinciden en varias series, clases y lead times. */
    @Test
    void calcular_sinPronostico_equivaleALaRutaDeTresArgumentosEnVariasSeries() {
        int[] dias = {0, 10, 29, 30, 45, 90};
        int[] totales = {0, 1, 7, 30, 150, 400};
        int[] leads = {0, 1, 7, 15, 45};
        Character[] clases = {'A', 'B', 'C', null};
        for (int n : dias) {
            for (int total : totales) {
                for (int lead : leads) {
                    for (Character clase : clases) {
                        SerieDemanda s = serie(Math.max(n, 1), total);
                        if (n == 0) {
                            s = SerieDemanda.de();
                        }
                        ResultadoReorden esperado = calculadora.calcular(s, lead, clase);
                        ReordenConFuente r = calculadora.calcular(s, lead, clase, null, CORTE_25_OCT);
                        assertThat(r.reorden()).as("n=%d total=%d L=%d clase=%s", n, total, lead, clase)
                                .isEqualTo(esperado);
                        assertThat(r.fuente()).isEqualTo(FuenteDemanda.HISTORICA);
                    }
                }
            }
        }
    }
}
