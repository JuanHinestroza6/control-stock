package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DistribucionPoissonTest {

    /** λ = 0: P(X = 0) = e^0 = 1 → el cuantil es 0 para cualquier p. */
    @Test
    void cuantil_lambdaCero_esCero() {
        assertThat(DistribucionPoisson.cuantil(0, 0.80)).isZero();
        assertThat(DistribucionPoisson.cuantil(0, 0.90)).isZero();
        assertThat(DistribucionPoisson.cuantil(0, 0.95)).isZero();
        assertThat(DistribucionPoisson.acumulada(0, 0)).isEqualTo(1.0);
    }

    /*
     * λ = 0,46:
     *   P(0) = e^−0,46 = 0,631284                    acumulado 0,631284
     *   P(1) = 0,631284 × 0,46 / 1 = 0,290391        acumulado 0,921674
     *   P(2) = 0,290391 × 0,46 / 2 = 0,066790        acumulado 0,988464
     * p = 0,80 → 1 ; p = 0,90 → 1 ; p = 0,95 → 2.
     */
    @Test
    void cuantil_lambda046() {
        assertThat(DistribucionPoisson.acumulada(0.46, 0)).isCloseTo(0.631284, within(1e-6));
        assertThat(DistribucionPoisson.acumulada(0.46, 1)).isCloseTo(0.921674, within(1e-6));
        assertThat(DistribucionPoisson.acumulada(0.46, 2)).isCloseTo(0.988464, within(1e-6));
        assertThat(DistribucionPoisson.cuantil(0.46, 0.80)).isEqualTo(1);
        assertThat(DistribucionPoisson.cuantil(0.46, 0.90)).isEqualTo(1);
        assertThat(DistribucionPoisson.cuantil(0.46, 0.95)).isEqualTo(2);
    }

    /*
     * λ = 2:
     *   P(0) = e^−2 = 0,135335   acum 0,135335
     *   P(1) = 0,270671          acum 0,406006
     *   P(2) = 0,270671          acum 0,676676
     *   P(3) = 0,180447          acum 0,857123
     *   P(4) = 0,090224          acum 0,947347
     *   P(5) = 0,036089          acum 0,983436
     * p = 0,80 → 3 ; p = 0,90 → 4 ; p = 0,95 → 5 (0,947347 < 0,95).
     */
    @Test
    void cuantil_lambda2() {
        assertThat(DistribucionPoisson.acumulada(2, 3)).isCloseTo(0.857123, within(1e-6));
        assertThat(DistribucionPoisson.acumulada(2, 4)).isCloseTo(0.947347, within(1e-6));
        assertThat(DistribucionPoisson.acumulada(2, 5)).isCloseTo(0.983436, within(1e-6));
        assertThat(DistribucionPoisson.cuantil(2, 0.80)).isEqualTo(3);
        assertThat(DistribucionPoisson.cuantil(2, 0.90)).isEqualTo(4);
        assertThat(DistribucionPoisson.cuantil(2, 0.95)).isEqualTo(5);
    }

    /** λ = 0,14: P(0) = e^−0,14 = 0,869358 ≥ 0,80 → 0 ; con p = 0,90: P(≤1) = 0,869358 × 1,14 = 0,991068 → 1. */
    @Test
    void cuantil_lambda014() {
        assertThat(DistribucionPoisson.cuantil(0.14, 0.80)).isZero();
        assertThat(DistribucionPoisson.cuantil(0.14, 0.90)).isEqualTo(1);
    }

    /** Frontera exacta: con λ = ln 2, P(0) = 0,5 → cuantil(λ, 0,5) = 0 (la tolerancia evita saltar a 1). */
    @Test
    void cuantil_acumuladoIgualAP_noSubeUnPaso() {
        assertThat(DistribucionPoisson.cuantil(Math.log(2), 0.5)).isZero();
    }

    @Test
    void acumulada_rNegativo_esCero() {
        assertThat(DistribucionPoisson.acumulada(1.5, -1)).isZero();
    }

    @Test
    void argumentosInvalidos_lanzaExcepcion() {
        assertThatThrownBy(() -> DistribucionPoisson.cuantil(-0.1, 0.9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistribucionPoisson.cuantil(Double.NaN, 0.9))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistribucionPoisson.cuantil(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistribucionPoisson.cuantil(1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DistribucionPoisson.cuantil(800, 0.9)).isInstanceOf(IllegalArgumentException.class);
    }
}
