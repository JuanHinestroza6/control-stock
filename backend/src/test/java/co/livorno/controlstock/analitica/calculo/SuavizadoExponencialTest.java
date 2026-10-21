package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class SuavizadoExponencialTest {

    private static final Offset<Double> EPS = within(1e-9);

    /*
     * Ejemplo del plan, α = 0,5 sobre 10, 12, 14:
     *   nivel_1 = 10
     *   nivel_2 = 0,5 × 12 + 0,5 × 10 = 11       error de un paso: 12 − 10 = 2
     *   nivel_3 = 0,5 × 14 + 0,5 × 11 = 12,5     error de un paso: 14 − 11 = 3
     *   SSE = 2² + 3² = 13 ; pronóstico = 12,5 en todo el horizonte
     */
    @Test
    void ajustar_alfaFijo_ejemploDelPlan() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {10, 12, 14}, 0.5);

        assertThat(ajuste.alfa()).isEqualTo(0.5);
        assertThat(ajuste.nivel()).isCloseTo(12.5, EPS);
        assertThat(ajuste.sse()).isCloseTo(13.0, EPS);
        assertThat(ajuste.pronosticar(3)).containsExactly(new double[] {12.5, 12.5, 12.5}, EPS);
    }

    /*
     * Rejilla sobre 10, 12, 14 (serie creciente: el α más alto sigue mejor la subida).
     *   α = 0,9: niveles 10 → 0,9×12 + 0,1×10 = 11,8 → 0,9×14 + 0,1×11,8 = 13,78
     *            SSE = (12 − 10)² + (14 − 11,8)² = 4 + 4,84 = 8,84
     *   α = 0,5: SSE 13 (prueba anterior) ; α = 0,1: SSE = 2² + (14 − 10,2)² = 4 + 14,44 = 18,44
     *   → gana α = 0,9, pronóstico 13,78
     */
    @Test
    void ajustar_rejilla_eligeElMenorSse() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {10, 12, 14});

        assertThat(ajuste.alfa()).isEqualTo(0.9);
        assertThat(ajuste.sse()).isCloseTo(8.84, EPS);
        assertThat(ajuste.nivel()).isCloseTo(13.78, EPS);
    }

    /*
     * Empate con SSE distinto de 0: con dos valores el SSE solo depende de nivel_1 = y_1:
     *   SSE = (20 − 10)² = 100 para todos los α → empate → menor α = 0,1
     *   nivel_2 = 0,1 × 20 + 0,9 × 10 = 11 → pronóstico 11
     */
    @Test
    void ajustar_empate_eligeElMenorAlfa() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {10, 20});

        assertThat(ajuste.alfa()).isEqualTo(0.1);
        assertThat(ajuste.sse()).isCloseTo(100.0, EPS);
        assertThat(ajuste.nivel()).isCloseTo(11.0, EPS);
    }

    /* Serie constante 7, 7, 7, 7: SSE 0 con todos los α → α 0,1; nivel 7 → pronóstico 7. */
    @Test
    void ajustar_serieConstante_pronosticoIgualAlValor() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {7, 7, 7, 7});

        assertThat(ajuste.alfa()).isEqualTo(0.1);
        assertThat(ajuste.sse()).isZero();
        assertThat(ajuste.pronosticar(3)).containsExactly(7.0, 7.0, 7.0);
    }

    /* Un solo valor: nivel_1 = y_1 = 5, sin errores de un paso (SSE 0) → α 0,1, pronóstico 5. */
    @Test
    void ajustar_unSoloValor_pronosticoIgualAlValor() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {5});

        assertThat(ajuste.alfa()).isEqualTo(0.1);
        assertThat(ajuste.sse()).isZero();
        assertThat(ajuste.pronosticar(2)).containsExactly(5.0, 5.0);
    }

    /* Serie toda en cero: nivel 0 → pronóstico 0 (el WAPE null lo decide el selector, A3). */
    @Test
    void ajustar_serieEnCero_pronosticoCero() {
        AjusteSes ajuste = SuavizadoExponencial.ajustar(new double[] {0, 0, 0, 0, 0});

        assertThat(ajuste.nivel()).isZero();
        assertThat(ajuste.pronosticar(3)).containsExactly(0.0, 0.0, 0.0);
    }

    /* Recorte a 0: un nivel negativo (no ocurre con y ≥ 0, defensa) pronostica max(0, −3) = 0. */
    @Test
    void pronosticar_nivelNegativo_recortaACero() {
        assertThat(new AjusteSes(0.5, -3.0, 0.0).pronosticar(2)).containsExactly(0.0, 0.0);
        assertThat(new AjusteSes(0.5, 4.0, 0.0).pronosticar(0)).isEmpty();
    }

    @Test
    void ajustar_serieInvalida_rechaza() {
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[0]))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacía");
        // Devoluciones mayores que ventas: la serie corregida ya viene recortada a 0; un negativo es un error.
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[] {3, -1, 2}))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posición 2");
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[] {3, Double.NaN}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[] {3, 4}, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AjusteSes(0.5, 1.0, 0.0).pronosticar(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* α fijo: 1 es válido (nivel = último valor); > 1, negativo y NaN se rechazan; serie nula e infinita también. */
    @Test
    void ajustar_alfaYSerieLimites_validaRango() {
        assertThat(SuavizadoExponencial.ajustar(new double[] {3, 8}, 1.0).nivel()).isCloseTo(8.0, EPS);
        for (double malo : new double[] {1.0001, -0.5, Double.NaN}) {
            assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[] {3, 4}, malo))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(new double[] {3, Double.POSITIVE_INFINITY}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SuavizadoExponencial.ajustar(null)).isInstanceOf(NullPointerException.class);
    }

    /* Horizonte 0 da arreglo vacío; horizonte largo (30) es constante. */
    @Test
    void pronosticar_horizonteLargo_constante() {
        AjusteSes ajuste = new AjusteSes(0.5, 4.0, 0.0);

        assertThat(ajuste.pronosticar(0)).isEmpty();
        assertThat(ajuste.pronosticar(30)).hasSize(30).containsOnly(4.0);
    }
}
