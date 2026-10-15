package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class SerieDemandaTest {

    private static final Offset<Double> EPS = within(1e-6);
    private static final LocalDate CORTE = LocalDate.of(2026, 10, 14);
    private static final LocalDate HACE_UN_ANIO = CORTE.minusDays(365);

    /** Variante creada el mismo día del corte: no hay ningún día completo de historia. */
    @Test
    void construir_sinHistoria_serieVacia() {
        SerieDemanda s = SerieDemanda.construir(Map.of(CORTE, 5), CORTE, CORTE, 90);

        assertThat(s.dias()).isZero();
        assertThat(s.total()).isZero();
        assertThat(s.media()).isZero();
        assertThat(s.desviacion()).isZero();
        assertThat(SerieDemanda.de().dias()).isZero();
    }

    /** Creada después del corte (reloj adelantado): n = 0, nunca negativo. */
    @Test
    void construir_creadaDespuesDelCorte_serieVacia() {
        assertThat(SerieDemanda.construir(Map.of(), CORTE.plusDays(3), CORTE, 90).dias()).isZero();
    }

    /** n = 1: d̄ = 4 ; s = 0 por definición (no hay n − 1 grados de libertad). */
    @Test
    void de_unSoloValor_desviacionCero() {
        SerieDemanda s = SerieDemanda.de(4);

        assertThat(s.dias()).isEqualTo(1);
        assertThat(s.media()).isEqualTo(4.0);
        assertThat(s.desviacion()).isZero();
    }

    /** 90 días sin ventas: d̄ = 0/90 = 0 ; s = 0. */
    @Test
    void construir_todaEnCero_mediaYDesviacionCero() {
        SerieDemanda s = SerieDemanda.construir(Map.of(), HACE_UN_ANIO, CORTE, 90);

        assertThat(s.dias()).isEqualTo(90);
        assertThat(s.valores()).containsOnly(0);
        assertThat(s.media()).isZero();
        assertThat(s.desviacion()).isZero();
    }

    /** Constante 3, 3, 3, 3: d̄ = 12/4 = 3 ; Σ (x − 3)² = 0 → s = 0. */
    @Test
    void de_serieConstante_desviacionCero() {
        SerieDemanda s = SerieDemanda.de(3, 3, 3, 3);

        assertThat(s.media()).isEqualTo(3.0);
        assertThat(s.desviacion()).isZero();
    }

    /*
     * Serie 2, 4, 4, 4, 5, 5, 7, 9 (n = 8): d̄ = 40/8 = 5.
     * Σ (x − 5)² = 9 + 1 + 1 + 1 + 0 + 0 + 4 + 16 = 32 ; s = sqrt(32 / 7) = sqrt(4,571429) = 2,138090.
     */
    @Test
    void de_ejemploAMano_desviacionMuestral() {
        SerieDemanda s = SerieDemanda.de(2, 4, 4, 4, 5, 5, 7, 9);

        assertThat(s.media()).isEqualTo(5.0);
        assertThat(s.desviacion()).isCloseTo(2.138090, EPS);
    }

    /*
     * Variante creada 3 días antes del corte (n = 3, días 11, 12 y 13 de octubre):
     *   día 11: vende 3 → 3 ; día 12: vende 1 y le devuelven 3 aptas → −2 → se recorta a 0 ; día 13: 1.
     * Serie [3, 0, 1]: d̄ = 4/3 = 1,333333.
     * Σ (x − d̄)² = 2,777778 + 1,777778 + 0,111111 = 4,666667 ; s = sqrt(4,666667 / 2) = 1,527525.
     */
    @Test
    void construir_devolucionesMayoresQueVentas_recortaACero() {
        Map<LocalDate, Integer> neto = Map.of(
                CORTE.minusDays(3), 3,
                CORTE.minusDays(2), -2,
                CORTE.minusDays(1), 1);

        SerieDemanda s = SerieDemanda.construir(neto, CORTE.minusDays(3), CORTE, 90);

        assertThat(s.valores()).containsExactly(3, 0, 1);
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.media()).isCloseTo(1.333333, EPS);
        assertThat(s.desviacion()).isCloseTo(1.527525, EPS);
        assertThat(SerieDemanda.de(3, -2, 1).valores()).containsExactly(3, 0, 1);
    }

    /*
     * Relleno de ceros (error frecuente): 3 ventas de 1 unidad en 90 días. Los 87 días sin filas cuentan como 0.
     * d̄ = 3/90 = 0,033333 (no 3/3 = 1).
     * Σ (x − d̄)² = Σ x² − n·d̄² = 3 − 90 × (1/30)² = 3 − 0,1 = 2,9 ; s = sqrt(2,9 / 89) = 0,180511.
     */
    @Test
    void construir_rellenaConCeros_tresVentasEn90Dias() {
        Map<LocalDate, Integer> neto = Map.of(
                CORTE.minusDays(80), 1,
                CORTE.minusDays(40), 1,
                CORTE.minusDays(5), 1);

        SerieDemanda s = SerieDemanda.construir(neto, HACE_UN_ANIO, CORTE, 90);

        assertThat(s.dias()).isEqualTo(90);
        assertThat(s.total()).isEqualTo(3);
        assertThat(s.media()).isCloseTo(3.0 / 90, EPS);
        assertThat(s.desviacion()).isCloseTo(0.180511, EPS);
    }

    /*
     * Ventana [corte − 90, corte): el día 90 hacia atrás entra (posición 0), el 91 no; el día del corte (en curso) no.
     * Historia corta: creada hace 10 días → n = 10 = min(90, 10); el día de creación cuenta (posición 0).
     */
    @Test
    void construir_respetaLaVentanaYLaFechaDeCreacion() {
        Map<LocalDate, Integer> neto = Map.of(
                CORTE.minusDays(91), 7,
                CORTE.minusDays(90), 2,
                CORTE.minusDays(1), 4,
                CORTE, 9);

        SerieDemanda larga = SerieDemanda.construir(neto, HACE_UN_ANIO, CORTE, 90);
        assertThat(larga.dias()).isEqualTo(90);
        assertThat(larga.valores()[0]).isEqualTo(2);
        assertThat(larga.valores()[89]).isEqualTo(4);
        assertThat(larga.total()).isEqualTo(6);

        SerieDemanda corta = SerieDemanda.construir(Map.of(CORTE.minusDays(10), 5, CORTE.minusDays(1), 1),
                CORTE.minusDays(10), CORTE, 90);
        assertThat(corta.dias()).isEqualTo(10);
        assertThat(corta.valores()).startsWith(5).endsWith(1);
        assertThat(corta.media()).isCloseTo(0.6, EPS);
    }

    @Test
    void construir_argumentosInvalidos_lanzaExcepcion() {
        assertThatThrownBy(() -> SerieDemanda.construir(Map.of(), CORTE, CORTE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SerieDemanda.construir(Map.of(), null, CORTE, 90))
                .isInstanceOf(NullPointerException.class);
    }
}
