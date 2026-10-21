package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DesagregadorTest {

    private static final double PISO = 0.5;

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static DemandaVariante variante(long id, long demanda, int conStock, int existentes) {
        return new DemandaVariante(id, demanda, new DiasConStock(conStock, existentes));
    }

    /*
     * Ejemplo a mano (ventana de 180 días):
     *   M (id 10): 60 u, con stock 180/180 → frac 1    → bruta 60 / 1   = 60
     *   L (id 11): 30 u, con stock  90/180 → frac 0,5  → bruta 30 / 0,5 = 60   (agotada la mitad: no es impopular)
     *   S (id 12): 10 u, con stock  45/180 → frac 0,25 → piso 0,5 → 10 / 0,5 = 20
     *   Σ = 140 → part = 3/7 = 0,428571 ; 3/7 ; 1/7 = 0,142857.
     * Mes 1, producto 70,00 → 30,00 / 30,00 / 10,00 (exacto).
     * Mes 2, producto 100,00 = 10.000 centésimas → exactos 4.285,71 / 4.285,71 / 1.428,57 → pisos 4.285 + 4.285 + 1.428
     *   = 9.998 ; faltan 2 → mayores residuos (0,714 de M y L) → 42,86 / 42,86 / 14,28 = 100,00.
     *   (Con HALF_UP por separado: 42,86 + 42,86 + 14,29 = 100,01.)
     */
    @Test
    void desagregar_ejemploAMano_corrigePorDiasConStock_ySumaElTotal() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("70.00"), bd("100.00")),
                List.of(variante(10, 60, 180, 180), variante(11, 30, 90, 180), variante(12, 10, 45, 180)), PISO);

        assertThat(r).extracting(PronosticoVariante::varianteId).containsExactly(10L, 11L, 12L);
        assertThat(r).extracting(v -> v.participacion().toPlainString())
                .containsExactly("0.428571", "0.428571", "0.142857");
        assertThat(r.get(0).pronostico()).containsExactly(bd("30.00"), bd("42.86"));
        assertThat(r.get(1).pronostico()).containsExactly(bd("30.00"), bd("42.86"));
        assertThat(r.get(2).pronostico()).containsExactly(bd("10.00"), bd("14.28"));
    }

    @Test
    void desagregar_devuelveEnOrdenAscendenteDeId() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("10.00")),
                List.of(variante(30, 1, 10, 10), variante(5, 1, 10, 10), variante(17, 2, 10, 10)), PISO);

        assertThat(r).extracting(PronosticoVariante::varianteId).containsExactly(5L, 17L, 30L);
        // Ordenadas por id: 5 (1 u), 17 (2 u), 30 (1 u) → part 1/4, 2/4, 1/4 → 2,50 / 5,00 / 2,50.
        assertThat(r).extracting(v -> v.pronostico().get(0)).containsExactly(bd("2.50"), bd("5.00"), bd("2.50"));
    }

    /*
     * Variante sin historia (creada en el corte: 0 días existentes, demanda 0) → part 0 y pronóstico 0,00 (tiene fila,
     * es activa). La otra recibe todo: 25,50.
     */
    @Test
    void desagregar_varianteSinHistoria_recibeCero() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("25.50")),
                List.of(variante(1, 40, 180, 180), variante(2, 0, 0, 0)), PISO);

        assertThat(r.get(0).participacion()).isEqualByComparingTo("1");
        assertThat(r.get(0).pronostico()).containsExactly(bd("25.50"));
        assertThat(r.get(1).participacion()).isEqualByComparingTo("0");
        assertThat(r.get(1).pronostico()).containsExactly(bd("0.00"));
    }

    /*
     * Variante creada hace 20 días con todos sus días con stock: frac = 20/20 = 1 (los días antes de la creación no
     * cuentan como agotados). Demanda 20 frente a 60 de la antigua → part 0,75 / 0,25 → 8,00 × part = 6,00 / 2,00.
     */
    @Test
    void desagregar_varianteNueva_soloCuentaSusDiasExistentes() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("8.00")),
                List.of(variante(1, 60, 180, 180), variante(2, 20, 20, 20)), PISO);

        assertThat(r).extracting(v -> v.pronostico().get(0)).containsExactly(bd("6.00"), bd("2.00"));
    }

    /*
     * Todas con participación 0 (sin ventas en 180 días, o devoluciones aptas mayores que ventas → neto negativo
     * recortado a 0): todas 0, aunque el producto tenga pronóstico (plan: "Total 0 → todas 0").
     */
    @Test
    void desagregar_todasConParticipacionCero_todasEnCero() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("12.00"), bd("15.00")),
                List.of(variante(1, 0, 180, 180), variante(2, -3, 100, 180)), PISO);

        assertThat(r).hasSize(2).allSatisfy(v -> {
            assertThat(v.participacion()).isEqualByComparingTo("0");
            assertThat(v.pronostico()).containsExactly(bd("0.00"), bd("0.00"));
        });
    }

    @Test
    void desagregar_pronosticoDelProductoCero_todasEnCero() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("0.00")),
                List.of(variante(1, 5, 180, 180), variante(2, 7, 180, 180)), PISO);

        assertThat(r).extracting(v -> v.pronostico().get(0)).containsExactly(bd("0.00"), bd("0.00"));
    }

    /* Empate de residuos: 0,01 entre dos variantes iguales → 0,5 centésimas cada una → la de menor id se la lleva. */
    @Test
    void desagregar_empateDeResiduos_menorIdPrimero() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("0.01")),
                List.of(variante(8, 3, 180, 180), variante(4, 3, 180, 180)), PISO);

        assertThat(r).extracting(PronosticoVariante::varianteId).containsExactly(4L, 8L);
        assertThat(r).extracting(v -> v.pronostico().get(0)).containsExactly(bd("0.01"), bd("0.00"));
    }

    /* Propiedad: con 7 variantes y totales "incómodos", la suma por mes es exacta y cada valor dista < 0,01 del exacto. */
    @Test
    void desagregar_sumaExactaPorMes_yErrorMenorQueUnaCentesima() {
        List<BigDecimal> producto = List.of(bd("123.45"), bd("0.07"), bd("999.99"));
        List<DemandaVariante> variantes = List.of(variante(1, 13, 180, 180), variante(2, 7, 170, 180),
                variante(3, 29, 60, 180), variante(4, 1, 180, 180), variante(5, 0, 180, 180),
                variante(6, 11, 120, 150), variante(7, 3, 30, 30));

        List<PronosticoVariante> r = Desagregador.desagregar(producto, variantes, PISO);
        double[] part = Desagregador.participaciones(variantes, PISO);

        for (int mes = 0; mes < producto.size(); mes++) {
            BigDecimal suma = BigDecimal.ZERO;
            for (int i = 0; i < r.size(); i++) {
                BigDecimal valor = r.get(i).pronostico().get(mes);
                suma = suma.add(valor);
                double exacto = producto.get(mes).doubleValue() * part[i];
                assertThat(Math.abs(valor.doubleValue() - exacto)).isLessThan(0.01);
            }
            assertThat(suma).isEqualByComparingTo(producto.get(mes));
        }
        assertThat(r.get(4).pronostico()).containsOnly(bd("0.00"));
    }

    @Test
    void desagregar_sinVariantes_listaVacia() {
        assertThat(Desagregador.desagregar(List.of(bd("5.00")), List.of(), PISO)).isEmpty();
    }

    @Test
    void validaciones() {
        List<DemandaVariante> una = List.of(variante(1, 1, 1, 1));
        assertThatThrownBy(() -> Desagregador.desagregar(List.of(bd("-1.00")), una, PISO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Desagregador.desagregar(List.of(bd("1.00")),
                List.of(variante(1, 1, 1, 1), variante(1, 2, 1, 1)), PISO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Desagregador.desagregar(List.of(bd("1.00")), una, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Desagregador.desagregar(List.of(bd("1.00")), una, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desagregar_unaSolaVariante_recibeTodoElPronosticoConParticipacionUno() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("33.33")),
                List.of(variante(7, 4, 100, 180)), PISO);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).participacion().toPlainString()).isEqualTo("1.000000");
        assertThat(r.get(0).pronostico()).containsExactly(bd("33.33"));
    }

    /* frac 0,1 y 0,4 quedan por debajo del piso 0,5: ambas se dividen por 0,5, así que pesan igual que una con frac 0,5. */
    @Test
    void desagregar_fraccionPorDebajoDelPiso_seUsaElPiso() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("10.00")),
                List.of(variante(1, 10, 18, 180), variante(2, 10, 72, 180), variante(3, 10, 90, 180)), PISO);

        // Brutas: 10/0,5 = 20 cada una → part 1/3. 1000 centésimas: 333 + 333 + 333 = 999; la que falta va al menor id.
        assertThat(r).extracting(v -> v.pronostico().get(0))
                .containsExactly(bd("3.34"), bd("3.33"), bd("3.33"));
    }

    @Test
    void desagregar_horizonteDeUnMes_unValorPorVariante() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("9.00")),
                List.of(variante(1, 1, 10, 10), variante(2, 2, 10, 10)), PISO);

        assertThat(r.get(0).pronostico()).containsExactly(bd("3.00"));
        assertThat(r.get(1).pronostico()).containsExactly(bd("6.00"));
    }

    /* Demanda neta negativa (devoluciones > ventas en la ventana) se trata como 0. */
    @Test
    void desagregar_demandaNegativa_cuentaComoCero() {
        List<PronosticoVariante> r = Desagregador.desagregar(List.of(bd("8.00")),
                List.of(variante(1, -5, 10, 10), variante(2, 3, 10, 10)), PISO);

        assertThat(r.get(0).pronostico()).containsExactly(bd("0.00"));
        assertThat(r.get(1).pronostico()).containsExactly(bd("8.00"));
    }
}
