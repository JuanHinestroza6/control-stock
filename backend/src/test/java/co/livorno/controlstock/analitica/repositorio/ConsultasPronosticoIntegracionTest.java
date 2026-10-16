package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.calculo.CorrectorCensura;
import co.livorno.controlstock.analitica.calculo.DiasConStock;
import co.livorno.controlstock.analitica.calculo.SaldoDisponible;
import co.livorno.controlstock.analitica.calculo.SerieMensual;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Consultas de demanda mensual y de disponibilidad del pronostico (B2, fase 6) contra MySQL real. Los datos se arman
 * con SQL porque las fechas deben ser exactas.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class ConsultasPronosticoIntegracionTest {

    static final YearMonth MES_ACTUAL = YearMonth.of(2026, 10);

    @Autowired ConsultasDemandaMensual demanda;
    @Autowired ConsultasDisponibilidad disponibilidad;
    @Autowired JdbcTemplate jdbc;

    Long usuarioId;
    Long productoId;
    Long variante1;
    Long variante2;
    String sufijo;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "cpr" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Pronostico', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, NOW(6), NOW(6))", "P" + sufijo, categoria);
        productoId = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "P" + sufijo);
        variante1 = crearVariante("P" + sufijo + "-M-NEG", "M", LocalDateTime.of(2026, 1, 1, 8, 0), true);
        variante2 = crearVariante("P" + sufijo + "-L-NEG", "L", LocalDateTime.of(2026, 1, 1, 8, 0), true);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)",
                productoId);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM movimiento WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)",
                productoId);
        jdbc.update("DELETE FROM variante WHERE producto_id = ?", productoId);
        jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long crearVariante(String sku, String talla, LocalDateTime creadoEn, boolean activa) {
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, ?, ?, ?)", productoId, sku, talla,
                activa, creadoEn, creadoEn);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private Long venta(LocalDateTime fecha, boolean anulada) {
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado, anulada_en, anulada_por, motivo_anulacion)"
                + " VALUES (?, ?, 0, ?, ?, ?, ?)", fecha, usuarioId, anulada ? "ANULADA" : "REGISTRADA",
                anulada ? fecha.plusHours(1) : null, anulada ? usuarioId : null, anulada ? "prueba" : null);
        return jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
    }

    private Long linea(Long ventaId, Long varianteId, int cantidad) {
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, 100.00, 10.00)", ventaId, varianteId, cantidad);
        return jdbc.queryForObject("SELECT id FROM detalle_venta WHERE venta_id = ? AND variante_id = ?", Long.class,
                ventaId, varianteId);
    }

    private void devolucion(Long detalleId, int cantidad, boolean apta, LocalDateTime fecha) {
        jdbc.update("INSERT INTO devolucion (detalle_venta_id, cantidad, motivo, apta_para_venta, fecha, usuario_id)"
                + " VALUES (?, ?, 'prueba', ?, ?, ?)", detalleId, cantidad, apta, fecha, usuarioId);
    }

    private void movimiento(Long varianteId, String tipo, int cantidad, int fisicoAntes, int fisicoDespues,
            int reservadoAntes, int reservadoDespues, LocalDateTime fecha) {
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues,"
                + " reservado_antes, reservado_despues, costo_unitario, usuario_id, fecha)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", varianteId, tipo, cantidad, fisicoAntes, fisicoDespues,
                reservadoAntes, reservadoDespues, "ENTRADA".equals(tipo) ? new BigDecimal("10.00") : null, usuarioId,
                fecha);
    }

    private Map<YearMonth, Integer> netoPorMes(int maxMeses) {
        return demanda.netoPorProductoYMes(MES_ACTUAL, maxMeses).stream()
                .filter(f -> f.productoId() == productoId)
                .collect(java.util.stream.Collectors.toMap(DemandaMensualProducto::mes, DemandaMensualProducto::neto));
    }

    private HistorialDisponibilidad historial(Long varianteId, LocalDate desde, LocalDate hasta) {
        return disponibilidad.historial(desde, hasta).stream().filter(h -> h.varianteId() == varianteId)
                .findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- demanda mensual

    @Test
    void netoPorProductoYMes_sumaVariantesPorMesYExcluyeAnuladas() {
        // dado: julio con dos variantes en dos ventas; agosto con una venta normal y una anulada
        Long v1 = venta(LocalDateTime.of(2026, 7, 2, 10, 0), false);
        linea(v1, variante1, 3);
        linea(v1, variante2, 2);
        Long v2 = venta(LocalDateTime.of(2026, 7, 31, 23, 59), false);
        linea(v2, variante1, 1);
        Long v3 = venta(LocalDateTime.of(2026, 8, 5, 9, 0), false);
        linea(v3, variante2, 4);
        Long anulada = venta(LocalDateTime.of(2026, 8, 6, 9, 0), true);
        linea(anulada, variante1, 50);

        // cuando / entonces: la anulada no cuenta
        assertThat(netoPorMes(3)).containsExactlyInAnyOrderEntriesOf(
                Map.of(YearMonth.of(2026, 7), 6, YearMonth.of(2026, 8), 4));
    }

    @Test
    void netoPorProductoYMes_devolucionAptaRestaEnSuMes_noAptaNo() {
        // dado: venta de julio por 10; devolucion apta de 2 en agosto (mes sin ventas) y de 3 en julio; no apta de 4
        Long v1 = venta(LocalDateTime.of(2026, 7, 10, 10, 0), false);
        Long detalle = linea(v1, variante1, 10);
        devolucion(detalle, 3, true, LocalDateTime.of(2026, 7, 12, 9, 0));
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 8, 3, 9, 0));
        devolucion(detalle, 4, false, LocalDateTime.of(2026, 7, 14, 9, 0));

        // cuando / entonces: julio = 10 - 3 (la no apta no resta); agosto = -2 se recorta a 0 (sin fila)
        assertThat(netoPorMes(3)).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 7), 7));
    }

    @Test
    void netoPorProductoYMes_devolucionDeVentaAnulada_noResta() {
        Long anulada = venta(LocalDateTime.of(2026, 7, 10, 10, 0), true);
        Long detalle = linea(anulada, variante1, 5);
        devolucion(detalle, 1, true, LocalDateTime.of(2026, 7, 11, 9, 0));
        Long normal = venta(LocalDateTime.of(2026, 7, 20, 10, 0), false);
        linea(normal, variante1, 2);

        assertThat(netoPorMes(3)).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 7), 2));
    }

    @Test
    void netoPorProductoYMes_mesEnCursoYMesesFueraDelMaximo_seExcluyen() {
        // dado: ventas en junio (fuera de 3 meses), septiembre (ultimo completo) y octubre (mes en curso)
        linea(venta(LocalDateTime.of(2026, 6, 30, 23, 59), false), variante1, 9);
        linea(venta(LocalDateTime.of(2026, 7, 1, 0, 0), false), variante1, 1);
        linea(venta(LocalDateTime.of(2026, 9, 30, 23, 59), false), variante1, 5);
        linea(venta(LocalDateTime.of(2026, 10, 1, 0, 0), false), variante1, 100);

        // cuando / entonces: [julio, octubre): el limite inferior es inclusivo y el mes en curso nunca entra
        assertThat(netoPorMes(3)).containsExactlyInAnyOrderEntriesOf(
                Map.of(YearMonth.of(2026, 7), 1, YearMonth.of(2026, 9), 5));
        assertThat(netoPorMes(4)).containsEntry(YearMonth.of(2026, 6), 9);
    }

    @Test
    void netoPorProductoYMes_conRelojInyectado_usaElMesActualDelClock() {
        // dado: una venta a mitad del mes anterior segun el reloj de la aplicacion
        YearMonth anterior = demanda.mesActual().minusMonths(1);
        linea(venta(anterior.atDay(15).atTime(12, 0), false), variante1, 4);

        // cuando
        Map<YearMonth, Integer> porMes = demanda.netoPorProductoYMes(2).stream()
                .filter(f -> f.productoId() == productoId)
                .collect(java.util.stream.Collectors.toMap(DemandaMensualProducto::mes, DemandaMensualProducto::neto));

        // entonces
        assertThat(porMes).containsExactlyEntriesOf(Map.of(anterior, 4));
    }

    @Test
    void netoPorProductoYMes_alimentaSerieMensual() {
        // dado: producto creado el 2026-07-15 (primer mes completo: agosto) con ventas en julio, agosto y septiembre
        linea(venta(LocalDateTime.of(2026, 7, 20, 10, 0), false), variante1, 8);
        linea(venta(LocalDateTime.of(2026, 8, 20, 10, 0), false), variante1, 6);
        linea(venta(LocalDateTime.of(2026, 9, 20, 10, 0), false), variante2, 7);
        Map<YearMonth, Integer> neto = netoPorMes(36);

        // cuando
        SerieMensual serie = SerieMensual.construir(neto, LocalDateTime.of(2026, 7, 15, 10, 0), MES_ACTUAL, 36);

        // entonces: agosto y septiembre
        assertThat(serie.valores()).containsExactly(6, 7);
        assertThat(serie.mesInicial()).isEqualTo(YearMonth.of(2026, 8));
    }

    @Test
    void netoPorVariante_sumaVentasMenosDevolucionesAptas_sinRecorte() {
        // dado: variante1 vende 10 y devuelve 3 aptas; variante2 solo tiene una devolucion apta de una venta previa
        Long v1 = venta(LocalDateTime.of(2026, 5, 10, 10, 0), false);
        Long d1 = linea(v1, variante1, 10);
        Long d2 = linea(v1, variante2, 5);
        devolucion(d1, 3, true, LocalDateTime.of(2026, 5, 12, 9, 0));
        devolucion(d2, 2, true, LocalDateTime.of(2026, 7, 2, 9, 0));
        linea(venta(LocalDateTime.of(2026, 7, 5, 10, 0), true), variante1, 99);

        // cuando
        Map<Long, Long> julio = demanda.netoPorVariante(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        Map<Long, Long> todo = demanda.netoPorVariante(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 1));

        // entonces: en julio solo cuenta la devolucion (negativo, el consumidor lo trata como 0)
        assertThat(julio).containsEntry(variante2, -2L).doesNotContainKey(variante1);
        assertThat(todo).containsEntry(variante1, 7L).containsEntry(variante2, 3L);
    }

    @Test
    void netoPorProductoYMes_ventaConvertidaDeReservas_cuentaEnLaFechaDeLaVenta() {
        // dado: reserva en junio y su conversion en venta en agosto (movimiento VENTA_RESERVA)
        Long v = venta(LocalDateTime.of(2026, 8, 18, 11, 0), false);
        linea(v, variante1, 3);
        movimiento(variante1, "VENTA_RESERVA", 3, 5, 2, 3, 0, LocalDateTime.of(2026, 8, 18, 11, 0));

        // cuando / entonces: cuenta como demanda en agosto, no en el mes de la reserva
        assertThat(netoPorMes(4)).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 8), 3));
    }

    @Test
    void netoPorProductoYMes_devolucionAptaEnOtroMes_restaEnSuMesYRecortaAlDejarloEnCero() {
        // dado: julio vende 5 y agosto vende 2; la devolucion apta de 5 (de la venta de julio) ocurre en agosto
        Long vJulio = venta(LocalDateTime.of(2026, 7, 10, 10, 0), false);
        Long detalle = linea(vJulio, variante1, 5);
        linea(venta(LocalDateTime.of(2026, 8, 10, 10, 0), false), variante2, 2);
        devolucion(detalle, 5, true, LocalDateTime.of(2026, 8, 20, 9, 0));

        // cuando / entonces: julio conserva 5; agosto = 2 - 5 = -3 se omite (recorte a 0)
        assertThat(netoPorMes(4)).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 7), 5));
    }

    @Test
    void netoPorProductoYMes_ventaA2330DelUltimoDiaDeAnio_cuentaEnDiciembre() {
        // dado: mes actual enero 2027; venta el 31/dic a las 23:30 y otra el 1/ene a las 00:00
        linea(venta(LocalDateTime.of(2026, 12, 31, 23, 30), false), variante1, 4);
        linea(venta(LocalDateTime.of(2027, 1, 1, 0, 0), false), variante1, 7);

        // cuando
        Map<YearMonth, Integer> porMes = demanda.netoPorProductoYMes(YearMonth.of(2027, 1), 2).stream()
                .filter(f -> f.productoId() == productoId)
                .collect(java.util.stream.Collectors.toMap(DemandaMensualProducto::mes, DemandaMensualProducto::neto));

        // entonces: diciembre = 4; el 1/ene es el mes en curso y no entra
        assertThat(porMes).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 12), 4));
    }

    @Test
    void netoPorProductoYMes_variasVentasDeLaMismaVarianteElMismoDia_sumanTodas() {
        // dado: tres ventas de la misma variante el mismo dia, una con dos lineas de variantes distintas
        Long a = venta(LocalDateTime.of(2026, 9, 3, 9, 0), false);
        linea(a, variante1, 2);
        linea(a, variante2, 1);
        linea(venta(LocalDateTime.of(2026, 9, 3, 12, 0), false), variante1, 3);
        linea(venta(LocalDateTime.of(2026, 9, 3, 18, 0), false), variante1, 4);

        // cuando / entonces
        assertThat(netoPorMes(2)).containsExactlyEntriesOf(Map.of(YearMonth.of(2026, 9), 10));
        assertThat(demanda.netoPorVariante(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                .containsEntry(variante1, 9L).containsEntry(variante2, 1L);
    }

    @Test
    void historial_ajusteQueDejaEnCeroYEntradaElMismoDia_entregaAmbosSaldosYElDiaCuentaConStock() {
        // dado: entrada de 4 el 5/ago; el 12/ago un ajuste negativo lo deja en 0 y una entrada de 6 lo repone
        movimiento(variante1, "ENTRADA", 4, 0, 4, 0, 0, LocalDateTime.of(2026, 8, 5, 9, 0));
        movimiento(variante1, "AJUSTE_NEGATIVO", 4, 4, 0, 0, 0, LocalDateTime.of(2026, 8, 12, 9, 0));
        movimiento(variante1, "ENTRADA", 6, 0, 6, 0, 0, LocalDateTime.of(2026, 8, 12, 15, 0));

        // cuando
        HistorialDisponibilidad h = historial(variante1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        // entonces: ambos saldos del dia 12 llegan en orden; el dia tuvo stock, asi que todo desde el 5 cuenta
        assertThat(h.saldos()).containsExactly(new SaldoDisponible(LocalDate.of(2026, 8, 5), 4),
                new SaldoDisponible(LocalDate.of(2026, 8, 12), 0), new SaldoDisponible(LocalDate.of(2026, 8, 12), 6));
        assertThat(CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(), YearMonth.of(2026, 8), 1))
                .containsExactly(new DiasConStock(27, 31));
    }

    @Test
    void historial_anulacionDeVentaQueRepone_seTrataPorSuSaldoDisponible() {
        // dado: la variante se agota con una venta y una ANULACION_VENTA (+fisico) la repone
        movimiento(variante1, "ENTRADA", 2, 0, 2, 0, 0, LocalDateTime.of(2026, 8, 1, 9, 0));
        movimiento(variante1, "VENTA", 2, 2, 0, 0, 0, LocalDateTime.of(2026, 8, 10, 9, 0));
        movimiento(variante1, "ANULACION_VENTA", 2, 0, 2, 0, 0, LocalDateTime.of(2026, 8, 20, 9, 0));

        // cuando
        HistorialDisponibilidad h = historial(variante1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        // entonces: el saldo tras la anulacion es 2 (la consulta no distingue tipos): con stock 1-9 y 20-31
        assertThat(h.saldos()).extracting(SaldoDisponible::disponible).containsExactly(2, 0, 2);
        assertThat(CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(), YearMonth.of(2026, 8), 1))
                .containsExactly(new DiasConStock(22, 31));
    }

    @Test
    void consultas_rangoInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> demanda.netoPorVariante(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> demanda.netoPorProductoYMes(MES_ACTUAL, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> disponibilidad.historial(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- disponibilidad

    @Test
    void historial_reservaQueDejaDisponibleEnCero_cuentaComoSinStock() {
        // dado: entrada de 5 el 10/ago; reserva de 5 el 20/ago (fisico 5, reservado 5 -> disponible 0); liberacion el 25
        movimiento(variante1, "ENTRADA", 5, 0, 5, 0, 0, LocalDateTime.of(2026, 8, 10, 9, 0));
        movimiento(variante1, "RESERVA", 5, 5, 5, 0, 5, LocalDateTime.of(2026, 8, 20, 9, 0));
        movimiento(variante1, "LIBERACION", 5, 5, 5, 5, 0, LocalDateTime.of(2026, 8, 25, 9, 0));

        // cuando
        HistorialDisponibilidad h = historial(variante1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        // entonces: el kardex entrega disponible 5, 0, 5 (no el fisico)
        assertThat(h.saldos()).containsExactly(new SaldoDisponible(LocalDate.of(2026, 8, 10), 5),
                new SaldoDisponible(LocalDate.of(2026, 8, 20), 0), new SaldoDisponible(LocalDate.of(2026, 8, 25), 5));
        assertThat(h.productoId()).isEqualTo(productoId);
        // y CorrectorCensura cuenta: 1-9 sin stock; 10-20 con stock (abre el 20 con 5); 21-24 sin stock; 25-31 con stock
        List<DiasConStock> dias = CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(),
                YearMonth.of(2026, 8), 1);
        assertThat(dias).containsExactly(new DiasConStock(18, 31));
    }

    @Test
    void historial_incluyeSoloElUltimoSaldoPrevioYExcluyeLosPosteriores() {
        // dado: movimientos en junio (dos), agosto y octubre; ventana agosto
        movimiento(variante1, "ENTRADA", 8, 0, 8, 0, 0, LocalDateTime.of(2026, 6, 1, 9, 0));
        movimiento(variante1, "VENTA", 8, 8, 0, 0, 0, LocalDateTime.of(2026, 6, 20, 9, 0));
        movimiento(variante1, "ENTRADA", 3, 0, 3, 0, 0, LocalDateTime.of(2026, 8, 15, 9, 0));
        movimiento(variante1, "VENTA", 3, 3, 0, 0, 0, LocalDateTime.of(2026, 10, 2, 9, 0));

        // cuando
        HistorialDisponibilidad h = historial(variante1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        // entonces: el ultimo previo (disponible 0 del 20/jun) abre la ventana; el de octubre queda fuera
        assertThat(h.saldos()).containsExactly(new SaldoDisponible(LocalDate.of(2026, 6, 20), 0),
                new SaldoDisponible(LocalDate.of(2026, 8, 15), 3));
        assertThat(CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(), YearMonth.of(2026, 8), 1))
                .containsExactly(new DiasConStock(17, 31));
    }

    @Test
    void historial_varianteCreadaAMitadDeMes_traeSuDiaDeCreacion() {
        // dado: variante creada el 16/ago a las 15:00 con una entrada ese mismo dia
        Long tardia = crearVariante("P" + sufijo + "-S-NEG", "S", LocalDateTime.of(2026, 8, 16, 15, 0), true);
        movimiento(tardia, "ENTRADA", 4, 0, 4, 0, 0, LocalDateTime.of(2026, 8, 16, 16, 0));

        // cuando
        HistorialDisponibilidad h = historial(tardia, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

        // entonces: existe desde el 16 (16 dias) y todos con stock; el mes anterior no existia
        assertThat(h.creadaEn()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(CorrectorCensura.diasConStockPorMes(h.saldos(), h.creadaEn(), YearMonth.of(2026, 7), 2))
                .containsExactly(new DiasConStock(0, 0), new DiasConStock(16, 16));
    }

    @Test
    void historial_varianteSinMovimientosEInactivas_seTratanAparte() {
        // dado: variante2 sin movimientos; una tercera inactiva con movimientos
        Long inactiva = crearVariante("P" + sufijo + "-XL-NEG", "XL", LocalDateTime.of(2026, 1, 1, 8, 0), false);
        movimiento(inactiva, "ENTRADA", 2, 0, 2, 0, 0, LocalDateTime.of(2026, 8, 2, 9, 0));

        // cuando
        List<HistorialDisponibilidad> todas = disponibilidad.historial(LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1));

        // entonces: la sin movimientos viene con saldos vacios; la inactiva no aparece
        assertThat(historial(variante2, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1)).saldos()).isEmpty();
        assertThat(todas).extracting(HistorialDisponibilidad::varianteId).doesNotContain(inactiva);
        assertThat(todas).extracting(HistorialDisponibilidad::varianteId).isSorted();
    }
}
