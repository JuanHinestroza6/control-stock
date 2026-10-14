package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CalculadoraReordenTest {

    /** Valores por defecto de application.yml: historia mínima 30, umbral Poisson 5, A/B/C 95-90-80 %. */
    static PropiedadesAnalitica propiedadesPorDefecto() {
        return new PropiedadesAnalitica("0 0 2 * * *", 365, 90, new BigDecimal("0.80"), new BigDecimal("0.95"), 30,
                new BigDecimal("5"), null);
    }

    private final CalculadoraReorden calculadora = new CalculadoraReorden(propiedadesPorDefecto());

    /** n días con {@code total} unidades vendidas de a una en los primeros días y el resto en 0. */
    private static SerieDemanda serie(int n, int total) {
        int[] valores = new int[n];
        for (int i = 0; i < total; i++) {
            valores[i % n]++;
        }
        return SerieDemanda.de(valores);
    }

    /*
     * Ejemplo 1 (NORMAL), clase A, L = 16. Serie de 31 días: 15 días con 1, 15 con 3 y uno con 2.
     *   d̄ = (15 + 45 + 2) / 31 = 62/31 = 2 ; Σ (x − 2)² = 15 + 15 + 0 = 30 ; s = sqrt(30/30) = 1.
     *   μL = 2 × 16 = 32 ≥ 5 → NORMAL ; SS = 1,65 × 1 × √16 = 6,6 ; ROP = ceil(32 + 6,6) = ceil(38,6) = 39.
     */
    @Test
    void calcular_ejemplo1_normalClaseA_rop39() {
        int[] valores = new int[31];
        Arrays.fill(valores, 0, 15, 1);
        Arrays.fill(valores, 15, 30, 3);
        valores[30] = 2;

        ResultadoReorden r = calculadora.calcular(SerieDemanda.de(valores), 16, 'A');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.NORMAL);
        assertThat(r.puntoReorden()).isEqualTo(39);
        assertThat(r.stockSeguridad().toPlainString()).isEqualTo("6.60");
        assertThat(r.demandaMedia().toPlainString()).isEqualTo("2.0000");
        assertThat(r.desviacion().toPlainString()).isEqualTo("1.0000");
        assertThat(r.diasHistoria()).isEqualTo(31);
        assertThat(r.leadTimeDias()).isEqualTo(16);
        assertThat(r.nivelServicio()).isEqualByComparingTo("0.95");
        assertThat(r.z()).isEqualByComparingTo("1.65");
    }

    /*
     * Ejemplo 2 (POISSON), clase C (p = 0,80). Una venta en 50 días, L = 23 → μL = 1 × 23 / 50 = 0,46 < 5.
     *   P(0) = 0,631 < 0,80 ; P(≤1) = 0,922 ≥ 0,80 → ROP = 1 ; SS = 1 − 0,46 = 0,54 (informativo).
     *   (La normal con ceil daría ceil(0,46 + 0,84 × 0,1414 × √23) = ceil(1,03) = 2: infla la cola.)
     */
    @Test
    void calcular_ejemplo2_poissonClaseC_rop1() {
        ResultadoReorden r = calculadora.calcular(serie(50, 1), 23, 'C');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.POISSON);
        assertThat(r.puntoReorden()).isEqualTo(1);
        assertThat(r.stockSeguridad().toPlainString()).isEqualTo("0.54");
        assertThat(r.demandaMedia().toPlainString()).isEqualTo("0.0200");
    }

    /*
     * Ejemplo 3 (POISSON), clase C. Una venta en 50 días, L = 7 → μL = 7/50 = 0,14.
     *   P(0) = e^−0,14 = 0,869 ≥ 0,80 → ROP = 0 (se repone al agotarse) ; SS = 0 − 0,14 = −0,14.
     */
    @Test
    void calcular_ejemplo3_poissonClaseC_rop0() {
        ResultadoReorden r = calculadora.calcular(serie(50, 1), 7, 'C');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.POISSON);
        assertThat(r.puntoReorden()).isZero();
        assertThat(r.stockSeguridad().toPlainString()).isEqualTo("-0.14");
    }

    /*
     * Ejemplo 4 (POISSON), clase A (p = 0,95). 6 unidades en 45 días, L = 15 → μL = 6 × 15 / 45 = 2.
     *   P(≤4) = 0,947 < 0,95 ; P(≤5) = 0,983 → ROP = 5 ; SS = 5 − 2 = 3.
     */
    @Test
    void calcular_ejemplo4_poissonClaseA_rop5() {
        ResultadoReorden r = calculadora.calcular(serie(45, 6), 15, 'A');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.POISSON);
        assertThat(r.puntoReorden()).isEqualTo(5);
        assertThat(r.stockSeguridad().toPlainString()).isEqualTo("3.00");
    }

    /** n = 29 < 30 → sin ROP (umbral = stockMinimo), SS null; con n = 30 ya se calcula. */
    @Test
    void calcular_historiaCorta_sinHistoria() {
        ResultadoReorden corta = calculadora.calcular(serie(29, 20), 15, 'A');

        assertThat(corta.metodo()).isEqualTo(MetodoReorden.SIN_HISTORIA);
        assertThat(corta.puntoReorden()).isNull();
        assertThat(corta.stockSeguridad()).isNull();
        assertThat(corta.diasHistoria()).isEqualTo(29);

        assertThat(calculadora.calcular(serie(30, 20), 15, 'A').puntoReorden()).isNotNull();
    }

    /** Serie vacía (variante creada hoy): n = 0 < 30 → SIN_HISTORIA con d̄ = s = 0. */
    @Test
    void calcular_serieVacia_sinHistoria() {
        ResultadoReorden r = calculadora.calcular(SerieDemanda.de(), 15, null);

        assertThat(r.metodo()).isEqualTo(MetodoReorden.SIN_HISTORIA);
        assertThat(r.puntoReorden()).isNull();
        assertThat(r.demandaMedia()).isEqualByComparingTo("0");
    }

    /** 90 días sin ventas → d̄ = 0 → ROP = 0 (solo AGOTADO), SS = 0. */
    @Test
    void calcular_demandaCero_sinDemanda() {
        ResultadoReorden r = calculadora.calcular(serie(90, 0), 15, 'B');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.SIN_DEMANDA);
        assertThat(r.puntoReorden()).isZero();
        assertThat(r.stockSeguridad().toPlainString()).isEqualTo("0.00");
    }

    /*
     * Clase null → parámetros de C. μL = 0,46 (ejemplo 2):
     *   null o C: p = 0,80 → ROP 1 ; A: p = 0,95 → P(≤1) = 0,922 < 0,95, P(≤2) = 0,988 → ROP 2.
     */
    @Test
    void calcular_claseNula_usaParametrosDeC() {
        ResultadoReorden sinClase = calculadora.calcular(serie(50, 1), 23, null);

        assertThat(sinClase.puntoReorden()).isEqualTo(1);
        assertThat(sinClase.nivelServicio()).isEqualByComparingTo("0.80");
        assertThat(sinClase.z()).isEqualByComparingTo("0.84");
        assertThat(calculadora.calcular(serie(50, 1), 23, 'A').puntoReorden()).isEqualTo(2);
    }

    /*
     * Frontera μL = 5 usa NORMAL: 15 unidades en 45 días (15 días con 1, 30 con 0), L = 15 → μL = 15 × 15 / 45 = 5.
     *   d̄ = 1/3 ; Σ (x − d̄)² = 15 × (2/3)² + 30 × (1/3)² = 6,6667 + 3,3333 = 10 ; s = sqrt(10/44) = 0,476731.
     *   Clase B: SS = 1,28 × 0,476731 × √15 = 1,28 × 0,476731 × 3,872983 = 2,363 ; ROP = ceil(7,363) = 8.
     * Con 14 unidades (μL = 4,67) ya es POISSON.
     */
    @Test
    void calcular_fronteraMuL5_usaNormal() {
        ResultadoReorden frontera = calculadora.calcular(serie(45, 15), 15, 'B');

        assertThat(frontera.metodo()).isEqualTo(MetodoReorden.NORMAL);
        assertThat(frontera.desviacion().toPlainString()).isEqualTo("0.4767");
        assertThat(frontera.stockSeguridad().toPlainString()).isEqualTo("2.36");
        assertThat(frontera.puntoReorden()).isEqualTo(8);

        assertThat(calculadora.calcular(serie(45, 14), 15, 'B').metodo()).isEqualTo(MetodoReorden.POISSON);
    }

    /*
     * Serie constante (2 unidades diarias por 30 días), L = 15: d̄ = 2, s = 0 → μL = 30, SS = 0 → ROP = ceil(30) = 30
     * (sin subir a 31 por redondeo).
     */
    @Test
    void calcular_serieConstante_ropIgualADemandaEnElLeadTime() {
        int[] valores = new int[30];
        Arrays.fill(valores, 2);

        ResultadoReorden r = calculadora.calcular(SerieDemanda.de(valores), 15, 'A');

        assertThat(r.metodo()).isEqualTo(MetodoReorden.NORMAL);
        assertThat(r.puntoReorden()).isEqualTo(30);
        assertThat(r.stockSeguridad()).isEqualByComparingTo("0");
    }

    /** Un lead time negativo es un error de datos, no un caso de negocio. */
    @Test
    void calcular_leadTimeNegativo_lanzaExcepcion() {
        assertThatThrownBy(() -> calculadora.calcular(serie(30, 3), -1, 'A'))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
