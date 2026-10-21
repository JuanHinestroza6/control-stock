package co.livorno.controlstock.analitica.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class CorrectorCensuraTest {

    private static final Offset<Double> EPS = within(1e-9);
    private static final double PISO = 0.5;
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9); // 30 días
    private static final LocalDate CREADA_EN_JULIO = LocalDate.of(2026, 7, 1);

    private static SaldoDisponible saldo(int mes, int dia, int disponible) {
        return new SaldoDisponible(LocalDate.of(2026, mes, dia), disponible);
    }

    private static DiasConStock septiembre(List<SaldoDisponible> saldos, LocalDate creadaEn) {
        return CorrectorCensura.diasConStock(saldos, creadaEn, SEPTIEMBRE.atDay(1), SEPTIEMBRE.plusMonths(1).atDay(1));
    }

    /* Entrada el 20 de agosto (disponible 10) y ningún movimiento más: septiembre 30 de 30 días con stock. */
    @Test
    void diasConStock_siempreConStock_fraccionUno() {
        DiasConStock dias = septiembre(List.of(saldo(8, 20, 10)), CREADA_EN_JULIO);

        assertThat(dias).isEqualTo(new DiasConStock(30, 30));
        assertThat(dias.fraccion()).isEqualTo(1.0);
    }

    /*
     * Variante sin ningún movimiento en el kardex: abre cada día en 0 (0 antes del primer movimiento) → 0 de 30.
     */
    @Test
    void diasConStock_sinMovimientos_todosSinStock() {
        assertThat(septiembre(List.of(), CREADA_EN_JULIO)).isEqualTo(new DiasConStock(0, 30));
    }

    /*
     * Día con entrada que repone (septiembre):
     *   20/08 entrada → 5
     *   05/09 venta agota → 0      día 5 abre en 5 → CON stock
     *   06..09 abren en 0, sin movimientos → 4 días SIN stock
     *   10/09 entrada repone → 6   día 10 abre en 0 pero un movimiento lo deja en 6 → CON stock
     *   11..30 abren en 6 → CON stock
     * con stock = 30 − 4 = 26 → frac = 26/30 = 0,866667
     */
    @Test
    void diasConStock_entradaQueRepone_eseDiaCuentaConStock() {
        List<SaldoDisponible> saldos = List.of(saldo(8, 20, 5), saldo(9, 5, 0), saldo(9, 10, 6));

        DiasConStock dias = septiembre(saldos, CREADA_EN_JULIO);

        assertThat(dias).isEqualTo(new DiasConStock(26, 30));
        assertThat(dias.fraccion()).isCloseTo(26.0 / 30, EPS);
    }

    /*
     * Movimientos que dejan el disponible en 0 a mitad del día y reposición que se vende el mismo día:
     *   20/08 entrada → 5
     *   20/09 reserva del último disponible → 0   día 20 abre en 5 → CON stock
     *   21..24 abren en 0 → 4 días SIN stock
     *   25/09 entrada → 2 y venta → 0           día 25 abre en 0, un movimiento lo deja en 2 → CON stock
     *   26..30 abren en 0 → 5 días SIN stock
     * con stock = 30 − 4 − 5 = 21
     */
    @Test
    void diasConStock_reposicionVendidaElMismoDia_cuentaConStockYElSiguienteNo() {
        List<SaldoDisponible> saldos = List.of(saldo(8, 20, 5), saldo(9, 20, 0), saldo(9, 25, 2), saldo(9, 25, 0));

        assertThat(septiembre(saldos, CREADA_EN_JULIO)).isEqualTo(new DiasConStock(21, 30));
    }

    /*
     * Variante creada a mitad de mes (16/09): existen 15 días (16..30; el día de creación cuenta).
     *   Con entrada el mismo 16/09 → 15 de 15 (frac 1, no 0,5: los días antes de la creación no cuentan).
     *   Con la primera entrada el 21/09 → 16..20 SIN stock (5), 21..30 CON stock (10) → 10 de 15.
     */
    @Test
    void diasConStock_varianteCreadaAMitadDeMes_soloCuentanSusDias() {
        LocalDate creada = LocalDate.of(2026, 9, 16);

        assertThat(septiembre(List.of(saldo(9, 16, 4)), creada)).isEqualTo(new DiasConStock(15, 15));
        assertThat(septiembre(List.of(saldo(9, 21, 4)), creada)).isEqualTo(new DiasConStock(10, 15));
    }

    /* Creada después del periodo: existentes 0, no existe y su fracción no está definida. */
    @Test
    void diasConStock_creadaDespuesDelPeriodo_noExiste() {
        DiasConStock dias = septiembre(List.of(saldo(10, 2, 3)), LocalDate.of(2026, 10, 2));

        assertThat(dias).isEqualTo(new DiasConStock(0, 0));
        assertThat(dias.existe()).isFalse();
        assertThatThrownBy(dias::fraccion).isInstanceOf(IllegalStateException.class);
    }

    /*
     * Ejemplo a mano del plan (septiembre, 30 días):
     *   V1 demanda 300 → w = 300/400 = 0,75 ; siempre con stock → frac 1
     *   V2 demanda 100 → w = 100/400 = 0,25 ; 20/08 → 5, 15/09 venta agota → 0: días 1..15 CON, 16..30 SIN → frac 0,5
     *   disp = (0,75 × 1 + 0,25 × 0,5) / (0,75 + 0,25) = 0,875
     *   observada 70 → corregida = 70 / max(0,875; 0,5) = 80
     */
    @Test
    void corregir_ejemploDelPlan_disponibilidad0875YCorregida80() {
        List<DiasConStock> v1 = CorrectorCensura.diasConStockPorMes(
                List.of(saldo(8, 20, 10)), CREADA_EN_JULIO, SEPTIEMBRE, 1);
        List<DiasConStock> v2 = CorrectorCensura.diasConStockPorMes(
                List.of(saldo(8, 20, 5), saldo(9, 15, 0)), CREADA_EN_JULIO, SEPTIEMBRE, 1);
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(300, v1), new DisponibilidadVariante(100, v2));

        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(SEPTIEMBRE, 70), variantes, PISO);

        assertThat(v2).containsExactly(new DiasConStock(15, 30));
        assertThat(r.disponibilidad()[0]).isCloseTo(0.875, EPS);
        assertThat(r.corregida()[0]).isCloseTo(80.0, EPS);
        assertThat(r.observada()).containsExactly(70);
        assertThat(r.mesInicial()).isEqualTo(SEPTIEMBRE);
        assertThat(r.totalObservada()).isEqualTo(70);
        assertThat(r.totalCorregida()).isCloseTo(80.0, EPS);
    }

    /*
     * Mes sin stock en todas las variantes: ambas agotan el 31/08 → septiembre 0 de 30 → disp = 0.
     *   corregida = 30 / max(0; 0,5) = 60 (×2 como máximo, el piso evita inflar sin límite).
     */
    @Test
    void corregir_mesSinStockEnTodas_aplicaElPiso() {
        List<SaldoDisponible> agotaAFinDeAgosto = List.of(saldo(8, 20, 3), saldo(8, 31, 0));
        List<DiasConStock> dias = CorrectorCensura.diasConStockPorMes(
                agotaAFinDeAgosto, CREADA_EN_JULIO, SEPTIEMBRE, 1);
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(10, dias), new DisponibilidadVariante(30, dias));

        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(SEPTIEMBRE, 30), variantes, PISO);

        assertThat(r.disponibilidad()[0]).isZero();
        assertThat(r.corregida()[0]).isCloseTo(60.0, EPS);
    }

    /*
     * Piso con disponibilidades dadas: 0,4 → max(0,4; 0,5) = 0,5 → 20 / 0,5 = 40 ;
     *   0,5 → 20 / 0,5 = 40 ; 0,8 → 20 / 0,8 = 25 ; 1 → 20.
     */
    @Test
    void corregir_disponibilidadBajoElPiso_usaElPiso() {
        SerieMensual serie = SerieMensual.de(SEPTIEMBRE, 20, 20, 20, 20);

        SerieCorregida r = CorrectorCensura.corregir(serie, new double[] {0.4, 0.5, 0.8, 1.0}, PISO);

        assertThat(r.corregida()).containsExactly(new double[] {40.0, 40.0, 25.0, 20.0}, EPS);
        assertThat(r.disponibilidad()).containsExactly(new double[] {0.4, 0.5, 0.8, 1.0}, EPS);
    }

    /* Observada 0: la corregida es 0 aunque la disponibilidad sea 0 (0 / 0,5 = 0; no se inventa demanda). */
    @Test
    void corregir_observadaCero_corregidaCero() {
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(SEPTIEMBRE, 0, 12), new double[] {0.0, 0.0},
                PISO);

        assertThat(r.corregida()).containsExactly(new double[] {0.0, 24.0}, EPS);
    }

    /*
     * Serie de dos meses (agosto 31 días, septiembre 30) con una variante creada a mitad de septiembre:
     *   V1 demanda 300 (w 0,75), existe desde julio: 31/07 agota → 0, 11/08 entrada → 8.
     *      agosto: 1..10 SIN, 11..31 CON → 21/31 ; septiembre 30/30.
     *   V2 demanda 100 (w 0,25), creada el 16/09 con entrada ese día: agosto no existe ; septiembre 15/15.
     *   disp agosto = 0,75 × 21/31 / 0,75 = 21/31 = 0,677419 (V2 no existe: no entra en el denominador)
     *   disp septiembre = (0,75 × 1 + 0,25 × 1) / 1 = 1
     *   observada [42, 50] → agosto 42 / (21/31) = 42 × 31 / 21 = 62 ; septiembre 50 / 1 = 50.
     */
    @Test
    void corregir_varianteCreadaAMitadDeLaSerie_soloPesaEnLosMesesQueExiste() {
        List<DiasConStock> v1 = CorrectorCensura.diasConStockPorMes(
                List.of(saldo(7, 10, 4), saldo(7, 31, 0), saldo(8, 11, 8)), CREADA_EN_JULIO, AGOSTO, 2);
        List<DiasConStock> v2 = CorrectorCensura.diasConStockPorMes(
                List.of(saldo(9, 16, 4)), LocalDate.of(2026, 9, 16), AGOSTO, 2);

        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 42, 50),
                List.of(new DisponibilidadVariante(300, v1), new DisponibilidadVariante(100, v2)), PISO);

        assertThat(v1).containsExactly(new DiasConStock(21, 31), new DiasConStock(30, 30));
        assertThat(v2).containsExactly(new DiasConStock(0, 0), new DiasConStock(15, 15));
        assertThat(r.disponibilidad()).containsExactly(new double[] {21.0 / 31, 1.0}, EPS);
        assertThat(r.corregida()).containsExactly(new double[] {62.0, 50.0}, EPS);
        assertThat(r.mes(1)).isEqualTo(SEPTIEMBRE);
    }

    /*
     * Producto sin demanda en toda la serie (total 0) → pesos iguales:
     *   V1 frac 1, V2 frac 0,5 → disp = (0,5 × 1 + 0,5 × 0,5) / 1 = 0,75.
     */
    @Test
    void disponibilidad_demandaTotalCero_pesosIguales() {
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(0, List.of(new DiasConStock(30, 30))),
                new DisponibilidadVariante(0, List.of(new DiasConStock(15, 30))));

        assertThat(CorrectorCensura.disponibilidad(variantes, 1)).containsExactly(new double[] {0.75}, EPS);
    }

    /*
     * Demanda negativa de una variante (devoluciones aptas de ventas anteriores a la serie) → se trata como 0:
     *   V1 −5 → w 0 ; V2 10 → w 1. disp = (0 × 1 + 1 × 0,5) / 1 = 0,5.
     */
    @Test
    void disponibilidad_demandaNegativa_pesaCero() {
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(-5, List.of(new DiasConStock(30, 30))),
                new DisponibilidadVariante(10, List.of(new DiasConStock(15, 30))));

        assertThat(CorrectorCensura.disponibilidad(variantes, 1)).containsExactly(new double[] {0.5}, EPS);
    }

    /*
     * Mes en el que solo existen variantes con peso 0 (V1, la que vende, se creó en septiembre):
     *   agosto: existe solo V2 (w 0) con 15/31 → Σ w = 0 → promedio simple = 15/31.
     *   septiembre: V1 30/30 (w 1), V2 15/30 (w 0) → disp = 1.
     */
    @Test
    void disponibilidad_soloVariantesSinPesoEnElMes_promedioSimple() {
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(100, List.of(new DiasConStock(0, 0), new DiasConStock(30, 30))),
                new DisponibilidadVariante(0, List.of(new DiasConStock(15, 31), new DiasConStock(15, 30))));

        assertThat(CorrectorCensura.disponibilidad(variantes, 2))
                .containsExactly(new double[] {15.0 / 31, 1.0}, EPS);
    }

    /*
     * Sin variantes activas (o ninguna existía en el mes): no hay evidencia de agotado → disp = 1 y no se corrige.
     */
    @Test
    void corregir_sinVariantes_noCorrige() {
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 8, 0, 3), List.of(), PISO);

        assertThat(r.disponibilidad()).containsExactly(1.0, 1.0, 1.0);
        assertThat(r.corregida()).containsExactly(8.0, 0.0, 3.0);
    }

    /* Serie vacía (producto nuevo): resultado vacío, sin errores. */
    @Test
    void corregir_serieVacia_resultadoVacio() {
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(SEPTIEMBRE), List.of(), PISO);

        assertThat(r.meses()).isZero();
        assertThat(r.corregida()).isEmpty();
        assertThat(r.totalCorregida()).isZero();
    }

    /* Serie constante con disponibilidad completa: la corrección no cambia nada. */
    @Test
    void corregir_serieConstanteConStockCompleto_igualALaObservada() {
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 6, 6, 6),
                new double[] {1.0, 1.0, 1.0}, PISO);

        assertThat(r.corregida()).containsExactly(6.0, 6.0, 6.0);
    }

    /* Piso = 1 es válido: la corregida iguala a la observada. */
    @Test
    void corregir_pisoUno_noCorrige() {
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 10, 10), new double[] {0.2, 1.0}, 1.0);

        assertThat(r.corregida()).containsExactly(new double[] {10.0, 10.0}, EPS);
    }

    @Test
    void corregir_disponibilidadNaNONegativa_lanzaExcepcion() {
        assertThatThrownBy(() -> CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 10), new double[] {Double.NaN},
                PISO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 10), new double[] {-0.1}, PISO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* Febrero bisiesto (2028): 29 días, con stock desde una entrada previa → 29 de 29. */
    @Test
    void diasConStockPorMes_febreroBisiesto_veintinueveDias() {
        List<DiasConStock> r = CorrectorCensura.diasConStockPorMes(
                List.of(new SaldoDisponible(LocalDate.of(2028, 1, 10), 5)), LocalDate.of(2027, 1, 1),
                YearMonth.of(2028, 2), 1);

        assertThat(r).containsExactly(new DiasConStock(29, 29));
    }

    /*
     * Frontera de año (dic 31 días, ene 31), creada 01/12: entrada 10/12 con 3 → agota 20/12 (el día 20 abre en 3: con
     * stock) → repone 05/01 (día 5 con stock).
     *   dic: sin stock 1..9 (9) y 21..31 (11) → con stock 31 − 20 = 11.
     *   ene: sin stock 1..4 (4) → con stock 27.
     */
    @Test
    void diasConStockPorMes_fronteraDeAnio_continuaElSaldoEntreMeses() {
        List<SaldoDisponible> saldos = List.of(
                new SaldoDisponible(LocalDate.of(2025, 12, 10), 3),
                new SaldoDisponible(LocalDate.of(2025, 12, 20), 0),
                new SaldoDisponible(LocalDate.of(2026, 1, 5), 4));

        List<DiasConStock> r = CorrectorCensura.diasConStockPorMes(saldos, LocalDate.of(2025, 12, 1),
                YearMonth.of(2025, 12), 2);

        assertThat(r).containsExactly(new DiasConStock(11, 31), new DiasConStock(27, 31));
    }

    @Test
    void diasConStockPorMes_cero_listaVaciaYNegativoLanza() {
        assertThat(CorrectorCensura.diasConStockPorMes(List.of(), CREADA_EN_JULIO, AGOSTO, 0)).isEmpty();
        assertThatThrownBy(() -> CorrectorCensura.diasConStockPorMes(List.of(), CREADA_EN_JULIO, AGOSTO, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.disponibilidad(List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* Creada el día 1 sin entrada: 30 existentes, 0 con stock. */
    @Test
    void diasConStock_creadaElDiaUnoSinEntrada_cuentaTodosSinStock() {
        assertThat(septiembre(List.of(), LocalDate.of(2026, 9, 1))).isEqualTo(new DiasConStock(0, 30));
    }

    /* Saldo previo en 0 deja el mes sin stock; saldos posteriores al periodo no influyen. */
    @Test
    void diasConStock_saldoPrevioEnCeroYSaldosPosteriores_sinStock() {
        List<SaldoDisponible> saldos = List.of(saldo(8, 31, 0), saldo(10, 5, 9));

        assertThat(septiembre(saldos, CREADA_EN_JULIO)).isEqualTo(new DiasConStock(0, 30));
    }

    /* Mes sin variantes existentes → disp 1 solo en ese mes; el siguiente se calcula normal. */
    @Test
    void disponibilidad_mesSinVariantesExistentes_unoYOtroMesNormal() {
        List<DisponibilidadVariante> variantes = List.of(new DisponibilidadVariante(10,
                List.of(new DiasConStock(0, 0), new DiasConStock(15, 30))));

        assertThat(CorrectorCensura.disponibilidad(variantes, 2)).containsExactly(new double[] {1.0, 0.5}, EPS);
    }

    /* Ponderación asimétrica: pesos 0,9 / 0,1, fracciones 0,2 / 1 → 0,18 + 0,1 = 0,28; 50 / 0,5 = 100. */
    @Test
    void corregir_ponderacionAsimetrica_pesaLaVarianteDeCabeza() {
        List<DisponibilidadVariante> variantes = List.of(
                new DisponibilidadVariante(90, List.of(new DiasConStock(6, 30))),
                new DisponibilidadVariante(10, List.of(new DiasConStock(30, 30))));

        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(SEPTIEMBRE, 50), variantes, PISO);

        assertThat(r.disponibilidad()[0]).isCloseTo(0.28, EPS);
        assertThat(r.corregida()[0]).isCloseTo(100.0, EPS);
    }

    /* Copias defensivas: modificar la entrada o las copias devueltas no altera el resultado. */
    @Test
    void serieCorregida_copiasDefensivas() {
        double[] disp = {1.0};
        SerieCorregida r = CorrectorCensura.corregir(SerieMensual.de(AGOSTO, 10), disp, PISO);
        disp[0] = 0.1;
        r.corregida()[0] = 999;
        r.observada()[0] = 999;

        assertThat(r.disponibilidad()).containsExactly(1.0);
        assertThat(r.corregida()).containsExactly(10.0);
        assertThat(r.observada()).containsExactly(10);
    }

    @Test
    void validaciones() {
        SerieMensual serie = SerieMensual.de(SEPTIEMBRE, 10);

        assertThatThrownBy(() -> CorrectorCensura.corregir(serie, new double[] {1.0}, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.corregir(serie, new double[] {1.0}, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.corregir(serie, new double[] {1.2}, PISO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.corregir(serie, new double[] {1.0, 1.0}, PISO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.disponibilidad(
                List.of(new DisponibilidadVariante(1, List.of())), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> septiembre(List.of(saldo(9, 10, 3), saldo(9, 5, 0)), CREADA_EN_JULIO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ordenados");
        assertThatThrownBy(() -> septiembre(List.of(saldo(10, 10, 3), saldo(10, 5, 0)), CREADA_EN_JULIO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectorCensura.diasConStock(List.of(), CREADA_EN_JULIO,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saldo(9, 1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiasConStock(5, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
