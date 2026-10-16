package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SerieMensualTest {

    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);

    /*
     * Producto creado el 2026-08-15 10:00, mes actual octubre:
     *   agosto es parcial (no cuenta), octubre está en curso (no cuenta) → solo septiembre, n = 1, y = [70].
     */
    @Test
    void construir_creadoAMitadDeMes_empiezaEnElMesSiguienteYExcluyeElMesActual() {
        Map<YearMonth, Integer> neto = Map.of(AGOSTO, 50, SEPTIEMBRE, 70, OCTUBRE, 20);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2026, 8, 15, 10, 0), OCTUBRE, 36);

        assertThat(s.meses()).isEqualTo(1);
        assertThat(s.mesInicial()).isEqualTo(SEPTIEMBRE);
        assertThat(s.valores()).containsExactly(70);
        assertThat(s.total()).isEqualTo(70);
    }

    /*
     * Creado el 1 de agosto a las 00:00 → agosto está completo y cuenta: n = 2 (agosto, septiembre).
     * Un segundo después ya no es mes completo: n = 1.
     */
    @Test
    void construir_creadoElDiaUnoAMedianoche_eseMesCuenta() {
        Map<YearMonth, Integer> neto = Map.of(AGOSTO, 50, SEPTIEMBRE, 70);

        SerieMensual exacto = SerieMensual.construir(neto, LocalDateTime.of(2026, 8, 1, 0, 0), OCTUBRE, 36);
        SerieMensual unSegundoDespues =
                SerieMensual.construir(neto, LocalDateTime.of(2026, 8, 1, 0, 0, 1), OCTUBRE, 36);

        assertThat(exacto.mesInicial()).isEqualTo(AGOSTO);
        assertThat(exacto.valores()).containsExactly(50, 70);
        assertThat(exacto.mes(1)).isEqualTo(SEPTIEMBRE);
        assertThat(unSegundoDespues.valores()).containsExactly(70);
    }

    /* Producto nuevo: creado en el mes en curso, a mitad del anterior o en el futuro → sin meses completos. */
    @Test
    void construir_productoNuevo_serieVacia() {
        Map<YearMonth, Integer> neto = Map.of(SEPTIEMBRE, 9, OCTUBRE, 4);

        SerieMensual delMes = SerieMensual.construir(neto, LocalDateTime.of(2026, 10, 3, 9, 0), OCTUBRE, 36);
        SerieMensual delAnterior = SerieMensual.construir(neto, LocalDateTime.of(2026, 9, 20, 9, 0), OCTUBRE, 36);
        SerieMensual futuro = SerieMensual.construir(neto, LocalDateTime.of(2027, 1, 1, 0, 0), OCTUBRE, 36);

        assertThat(delMes.meses()).isZero();
        assertThat(delMes.total()).isZero();
        assertThat(delMes.mesInicial()).isEqualTo(OCTUBRE);
        assertThat(delAnterior.meses()).isZero();
        assertThat(futuro.meses()).isZero();
        assertThat(delMes.tieneMesEnCero()).isFalse();
    }

    /*
     * Creado en 2020 con mes actual 2026-10: la historia se recorta a 36 meses, [2023-10, 2026-10).
     * El neto de 2023-09 (fuera) no entra; el de 2023-10 es el primer valor.
     */
    @Test
    void construir_historiaLarga_seRecortaAlMaximoDeMeses() {
        Map<YearMonth, Integer> neto = Map.of(YearMonth.of(2023, 9), 99, YearMonth.of(2023, 10), 11,
                SEPTIEMBRE, 22);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2020, 1, 1, 0, 0), OCTUBRE, 36);

        assertThat(s.meses()).isEqualTo(36);
        assertThat(s.mesInicial()).isEqualTo(YearMonth.of(2023, 10));
        assertThat(s.valor(0)).isEqualTo(11);
        assertThat(s.valor(35)).isEqualTo(22);
        assertThat(s.mes(35)).isEqualTo(SEPTIEMBRE);
        assertThat(s.total()).isEqualTo(33);
    }

    /*
     * Creado el 2026-06-10 → meses julio, agosto, septiembre.
     *   julio 10 ; agosto −3 (devolvieron 5 aptas de ventas de julio y vendieron 2) → 0 ; septiembre sin filas → 0.
     *   y = [10, 0, 0], total 10.
     */
    @Test
    void construir_netoNegativoYMesesSinFilas_seRecortanACero() {
        Map<YearMonth, Integer> neto = new HashMap<>();
        neto.put(YearMonth.of(2026, 7), 10);
        neto.put(AGOSTO, -3);
        neto.put(null, 50);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2026, 6, 10, 8, 0), OCTUBRE, 36);

        assertThat(s.valores()).containsExactly(10, 0, 0);
        assertThat(s.total()).isEqualTo(10);
        assertThat(s.tieneMesEnCero()).isTrue();
    }

    /* Valor null en la consulta → 0; serie toda en cero tiene total 0. */
    @Test
    void construir_valorNullYSerieTodaEnCero() {
        Map<YearMonth, Integer> neto = new HashMap<>();
        neto.put(SEPTIEMBRE, null);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2026, 6, 10, 8, 0), OCTUBRE, 36);

        assertThat(s.valores()).containsExactly(0, 0, 0);
        assertThat(s.total()).isZero();
    }

    /* Serie constante 5, 5, 5: total 15, ningún mes en cero; de() recorta negativos; valores() es una copia. */
    @Test
    void de_serieConstanteYCopiaDefensiva() {
        SerieMensual s = SerieMensual.de(AGOSTO, 5, 5, 5);
        int[] copia = s.valores();
        copia[0] = 100;

        assertThat(s.valores()).containsExactly(5, 5, 5);
        assertThat(s.total()).isEqualTo(15);
        assertThat(s.tieneMesEnCero()).isFalse();
        assertThat(s.mes(2)).isEqualTo(YearMonth.of(2026, 10));
        assertThat(SerieMensual.de(AGOSTO, -4, 6).valores()).containsExactly(0, 6);
        assertThat(SerieMensual.de(AGOSTO, 7).meses()).isEqualTo(1);
    }

    /* Límite maxMeses = 1: solo el último mes completo, aunque haya más historia. */
    @Test
    void construir_maxMesesUno_soloElUltimoMesCompleto() {
        Map<YearMonth, Integer> neto = Map.of(AGOSTO, 50, SEPTIEMBRE, 70);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2020, 1, 1, 0, 0), OCTUBRE, 1);

        assertThat(s.mesInicial()).isEqualTo(SEPTIEMBRE);
        assertThat(s.valores()).containsExactly(70);
    }

    /* maxMeses mayor que la historia: manda la historia real (creado 01/08 00:00 → 2 meses de 36). */
    @Test
    void construir_maxMesesMayorQueLaHistoria_mandaLaHistoria() {
        SerieMensual s = SerieMensual.construir(Map.of(AGOSTO, 1, SEPTIEMBRE, 2),
                LocalDateTime.of(2026, 8, 1, 0, 0), OCTUBRE, 36);

        assertThat(s.meses()).isEqualTo(2);
    }

    /* Creado el 1 del mes anterior a las 00:00: ese mes es el único; creado el 1 del mes actual: vacía. */
    @Test
    void construir_creadoElDiaUnoDelMesAnteriorOdelActual_fronteras() {
        Map<YearMonth, Integer> neto = Map.of(SEPTIEMBRE, 7, OCTUBRE, 3);

        SerieMensual anterior = SerieMensual.construir(neto, LocalDateTime.of(2026, 9, 1, 0, 0), OCTUBRE, 36);
        SerieMensual actual = SerieMensual.construir(neto, LocalDateTime.of(2026, 10, 1, 0, 0), OCTUBRE, 36);

        assertThat(anterior.valores()).containsExactly(7);
        assertThat(actual.meses()).isZero();
    }

    /* Frontera de año: creado el 20/11/2025, mes actual enero 2026 → solo diciembre; con febrero, diciembre y enero. */
    @Test
    void construir_fronteraDeAnio_incluyeDiciembreYExcluyeEnero() {
        YearMonth dic = YearMonth.of(2025, 12);
        YearMonth ene = YearMonth.of(2026, 1);
        Map<YearMonth, Integer> neto = Map.of(dic, 8, ene, 5);

        SerieMensual s = SerieMensual.construir(neto, LocalDateTime.of(2025, 11, 20, 9, 0), ene, 36);
        SerieMensual dos = SerieMensual.construir(neto, LocalDateTime.of(2025, 11, 20, 9, 0), ene.plusMonths(1), 36);

        assertThat(s.mesInicial()).isEqualTo(dic);
        assertThat(s.valores()).containsExactly(8);
        assertThat(dos.valores()).containsExactly(8, 5);
        assertThat(dos.mes(1)).isEqualTo(ene);
    }

    /* tieneMesEnCero detecta un cero en medio; de() sin valores es una serie vacía. */
    @Test
    void tieneMesEnCero_cerosEnElMedioYSerieVacia() {
        assertThat(SerieMensual.de(AGOSTO, 3, 0, 4).tieneMesEnCero()).isTrue();
        assertThat(SerieMensual.de(AGOSTO).meses()).isZero();
        assertThat(SerieMensual.de(AGOSTO).tieneMesEnCero()).isFalse();
    }

    @Test
    void validaciones() {
        LocalDateTime creado = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertThatThrownBy(() -> SerieMensual.construir(Map.of(), creado, OCTUBRE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SerieMensual.construir(null, creado, OCTUBRE, 36))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SerieMensual.de(AGOSTO, 1).mes(1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
