package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClasificadorAbcTest {

    private static final BigDecimal CORTE_A = new BigDecimal("0.80");
    private static final BigDecimal CORTE_B = new BigDecimal("0.95");

    private static IngresoVariante ingreso(long id, String valor) {
        return new IngresoVariante(id, new BigDecimal(valor));
    }

    private static List<ClasificacionAbc> clasificar(IngresoVariante... ingresos) {
        return ClasificadorAbc.clasificar(List.of(ingresos), CORTE_A, CORTE_B);
    }

    /*
     * Ejemplo del plan. Total = 500 + 200 + 150 + 100 + 50 = 1000.
     *   pos  id  ingreso  participación  acumulado            clase
     *   1    4   500      0,50           0,50                  A (≤ 0,80; además es la primera)
     *   2    2   200      0,20           0,70                  A (≤ 0,80)
     *   3    5   150      0,15           0,85                  B (> 0,80 y ≤ 0,95)
     *   4    1   100      0,10           0,95                  B (frontera: ≤ 0,95)
     *   5    3    50      0,05           1,00                  C
     */
    @Test
    void clasificar_ejemploDelPlan_AABBC() {
        List<ClasificacionAbc> r = clasificar(ingreso(1, "100"), ingreso(2, "200"), ingreso(3, "50"),
                ingreso(4, "500"), ingreso(5, "150"));

        assertThat(r).extracting(ClasificacionAbc::varianteId).containsExactly(4L, 2L, 5L, 1L, 3L);
        assertThat(r).extracting(ClasificacionAbc::posicion).containsExactly(1, 2, 3, 4, 5);
        assertThat(r).extracting(c -> c.participacion().toPlainString())
                .containsExactly("0.500000", "0.200000", "0.150000", "0.100000", "0.050000");
        assertThat(r).extracting(c -> c.acumulado().toPlainString())
                .containsExactly("0.500000", "0.700000", "0.850000", "0.950000", "1.000000");
        assertThat(r).extracting(ClasificacionAbc::clase).containsExactly('A', 'A', 'B', 'B', 'C');
        assertThat(r.get(0).ingresoNeto().toPlainString()).isEqualTo("500.00");
    }

    /*
     * La primera supera sola el 80 %: total 1000; acumulados 0,90 / 0,96 / 1,00.
     * Posición 1 → A aunque 0,90 > 0,80. 0,96 > 0,95 → C. 1,00 → C. No hay B.
     */
    @Test
    void clasificar_primeraSuperaElCorteA_igualEsA() {
        List<ClasificacionAbc> r = clasificar(ingreso(10, "900"), ingreso(11, "60"), ingreso(12, "40"));

        assertThat(r).extracting(c -> c.acumulado().toPlainString())
                .containsExactly("0.900000", "0.960000", "1.000000");
        assertThat(r).extracting(ClasificacionAbc::clase).containsExactly('A', 'C', 'C');
    }

    /** Una sola variante con ingreso: participación y acumulado 1 → A. */
    @Test
    void clasificar_unaSolaVariante_esA() {
        List<ClasificacionAbc> r = clasificar(ingreso(7, "1234.5"));

        assertThat(r).singleElement().satisfies(c -> {
            assertThat(c.clase()).isEqualTo('A');
            assertThat(c.acumulado()).isEqualByComparingTo("1");
            assertThat(c.ingresoNeto().toPlainString()).isEqualTo("1234.50");
        });
    }

    /*
     * Empates: tres variantes con 100 (ids 7, 3, 5) y una con 0 (id 1). Total 300.
     * Orden: ingreso desc y, empatadas, id asc → 3, 5, 7, 1.
     * Acumulados: 100/300 = 0,333333 (A), 200/300 = 0,666667 (A), 300/300 = 1,000000 (C), 1,000000 con ingreso 0 (C).
     */
    @Test
    void clasificar_empates_seDesempatanPorIdDeVariante() {
        List<ClasificacionAbc> r = clasificar(ingreso(7, "100"), ingreso(1, "0"), ingreso(3, "100.00"),
                ingreso(5, "100"));

        assertThat(r).extracting(ClasificacionAbc::varianteId).containsExactly(3L, 5L, 7L, 1L);
        assertThat(r).extracting(c -> c.acumulado().toPlainString())
                .containsExactly("0.333333", "0.666667", "1.000000", "1.000000");
        assertThat(r).extracting(ClasificacionAbc::clase).containsExactly('A', 'A', 'C', 'C');
        assertThat(r.get(3).participacion()).isEqualByComparingTo("0");
    }

    /*
     * Ingreso 0 → C siempre. Total 100: id 2 = 100 (A por ser la primera), ids 3 y 4 = 0 → C.
     */
    @Test
    void clasificar_ingresoCero_esC() {
        List<ClasificacionAbc> r = clasificar(ingreso(4, "0"), ingreso(2, "100"), ingreso(3, "0"));

        assertThat(r).extracting(ClasificacionAbc::varianteId).containsExactly(2L, 3L, 4L);
        assertThat(r).extracting(ClasificacionAbc::clase).containsExactly('A', 'C', 'C');
    }

    /** Total 0 (nadie vendió en la ventana): todas C, participación y acumulado 0, posiciones por id. */
    @Test
    void clasificar_totalCero_todasC() {
        List<ClasificacionAbc> r = clasificar(ingreso(9, "0"), ingreso(2, "0.00"), ingreso(5, "0"));

        assertThat(r).extracting(ClasificacionAbc::varianteId).containsExactly(2L, 5L, 9L);
        assertThat(r).extracting(ClasificacionAbc::posicion).containsExactly(1, 2, 3);
        assertThat(r).extracting(ClasificacionAbc::clase).containsExactly('C', 'C', 'C');
        assertThat(r).allSatisfy(c -> {
            assertThat(c.participacion().toPlainString()).isEqualTo("0.000000");
            assertThat(c.acumulado().toPlainString()).isEqualTo("0.000000");
        });
    }

    @Test
    void clasificar_listaVacia_devuelveVacia() {
        assertThat(ClasificadorAbc.clasificar(List.of(), CORTE_A, CORTE_B)).isEmpty();
    }

    /*
     * Redondeo: tres variantes de 1 (total 3). Sumar participaciones redondeadas daría 0,333333 × 3 = 0,999999;
     * el acumulado se calcula con el ingreso acumulado exacto: 1/3 = 0,333333, 2/3 = 0,666667, 3/3 = 1,000000.
     */
    @Test
    void clasificar_acumuladoFinalSiempreEsUno() {
        List<ClasificacionAbc> r = clasificar(ingreso(1, "1"), ingreso(2, "1"), ingreso(3, "1"));

        assertThat(r).extracting(c -> c.participacion().toPlainString())
                .containsExactly("0.333333", "0.333333", "0.333333");
        assertThat(r.get(2).acumulado().toPlainString()).isEqualTo("1.000000");
    }

    @Test
    void clasificar_entradasInvalidas_lanzaExcepcion() {
        assertThatThrownBy(() -> clasificar(ingreso(1, "-5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
        assertThatThrownBy(() -> clasificar(ingreso(1, "5"), ingreso(1, "7")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetida");
        assertThatThrownBy(() -> clasificar(new IngresoVariante(1, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClasificadorAbc.clasificar(List.of(), CORTE_B, CORTE_A))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
