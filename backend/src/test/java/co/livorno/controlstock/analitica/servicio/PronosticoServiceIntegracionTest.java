package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.analitica.repositorio.PronosticoRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link PronosticoService#calcularYGuardar} (B3, fase 6) contra MySQL real, con datos armados por SQL para controlar
 * las fechas. Corte 2026-10-15: la serie va de 2023-10 a 2026-09 (36 meses completos).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class PronosticoServiceIntegracionTest {

    static final LocalDate CORTE = LocalDate.of(2026, 10, 15);
    static final YearMonth INICIO_SERIE = YearMonth.of(2023, 10);
    static final YearMonth FIN_SERIE = YearMonth.of(2026, 9);
    /** Estacionalidad de media 1 (enero a diciembre) x 100 unidades por mes; multiplos de 10 para repartir 7:3. */
    static final int[] UNIDADES_POR_MES = {60, 70, 80, 90, 100, 100, 110, 110, 120, 120, 140, 100};

    @Autowired PronosticoService servicio;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PronosticoRepository pronosticosEspia;

    Long usuarioId;
    Long categoriaId;
    String sufijo;
    int contador;
    final List<Long> productosCreados = new ArrayList<>();
    final List<Long> ejecucionesCreadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "psv" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Pronostico', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        categoriaId = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        for (Long id : ejecucionesCreadas) {
            jdbc.update("DELETE FROM ejecucion_analitica WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM venta WHERE usuario_id = ?)",
                usuarioId);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        for (Long productoId : productosCreados) {
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)",
                    productoId);
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", productoId);
            jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    // ---------------------------------------------------------------- datos

    private Long ejecucion() {
        Long id = ejecuciones.saveAndFlush(EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null,
                LocalDateTime.of(2026, 10, 15, 2, 0), CORTE)).getId();
        ejecucionesCreadas.add(id);
        // Cierra la ejecucion para que no estorbe a otra EN_CURSO (una a la vez).
        jdbc.update("UPDATE ejecucion_analitica SET estado = 'FALLIDA', terminada_en = ?, mensaje = 'prueba' WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 10, 15, 2, 1)), id);
        return id;
    }

    private Long producto(LocalDateTime creadoEn, boolean activo) {
        String codigo = "PS" + sufijo + (contador++);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, activo, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, ?, ?, ?)", codigo, categoriaId, activo,
                creadoEn, creadoEn);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
        productosCreados.add(id);
        return id;
    }

    /** Variante con una entrada grande al crearla: siempre con stock (disponibilidad 1). */
    private Long variante(Long productoId, String talla, boolean activa, LocalDateTime creadoEn) {
        String sku = "PS" + sufijo + "-" + (contador++) + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 100000, 0, ?, ?, ?)", productoId, sku, talla,
                activa, creadoEn, creadoEn);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,"
                + " reservado_despues, costo_unitario, usuario_id, fecha) VALUES (?, 'ENTRADA', 100000, 0, 100000, 0, 0,"
                + " 10.00, ?, ?)", id, usuarioId, creadoEn.plusHours(1));
        return id;
    }

    private void vender(Long varianteId, YearMonth mes, int cantidad) {
        vender(varianteId, mes, cantidad, "REGISTRADA");
    }

    private void vender(Long varianteId, YearMonth mes, int cantidad, String estado) {
        LocalDateTime fecha = mes.atDay(10).atTime(12, 0);
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado, anulada_en, anulada_por, motivo_anulacion)"
                + " VALUES (?, ?, 0, ?, ?, ?, ?)", fecha, usuarioId, estado,
                "ANULADA".equals(estado) ? fecha.plusHours(1) : null,
                "ANULADA".equals(estado) ? usuarioId : null, "ANULADA".equals(estado) ? "prueba" : null);
        Long ventaId = jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, 100.00, 10.00)", ventaId, varianteId, cantidad);
    }

    /** 36 meses de 100 x S: 70 % a la primera variante y 30 % a la segunda. Omite {@code mesEnCero} si no es null. */
    private void ventasEstacionales(Long v1, Long v2, YearMonth mesEnCero) {
        for (YearMonth m = INICIO_SERIE; !m.isAfter(FIN_SERIE); m = m.plusMonths(1)) {
            if (m.equals(mesEnCero)) {
                continue;
            }
            int unidades = UNIDADES_POR_MES[m.getMonthValue() - 1];
            vender(v1, m, unidades / 10 * 7);
            vender(v2, m, unidades / 10 * 3);
        }
    }

    private static final LocalDateTime CREACION_ANTIGUA = LocalDateTime.of(2023, 1, 1, 0, 0);

    private int contar(String tabla, Long productoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE producto_id = ?", Integer.class,
                productoId);
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void calcularYGuardar_producto36MesesSinMesesEnCero_holtWintersYDesagregaALasVariantes() {
        // dado: demanda 100 x S exacta (estacional multiplicativa), dos variantes activas 70/30 y una inactiva
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        Long inactiva = variante(p, "S", false, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, null);
        Long ejecucionId = ejecucion();

        // cuando
        ResultadoPronosticoGuardado r = servicio.calcularYGuardar(ejecucionId, CORTE);

        // entonces: HW elegido, con parametros y WAPE de ambos metodos
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT metodo, beta, wape_hw, wape_ses, motivo_sin_hw, meses_historia, ejecucion_id"
                        + " FROM analitica_producto WHERE producto_id = ?", p);
        assertThat(fila.get("metodo")).isEqualTo("HOLT_WINTERS");
        assertThat(fila.get("beta")).isNotNull();
        assertThat(fila.get("wape_hw")).isNotNull();
        assertThat(fila.get("wape_ses")).isNotNull();
        assertThat(fila.get("motivo_sin_hw")).isNull();
        assertThat(fila.get("meses_historia")).isEqualTo(36);
        assertThat(((Number) fila.get("ejecucion_id")).longValue()).isEqualTo(ejecucionId);

        // serie mensual guardada: 36 filas; backtest solo en los ultimos 6 meses
        assertThat(contar("analitica_producto_mes", p)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto_mes WHERE producto_id = ?"
                + " AND backtest_ses IS NOT NULL AND backtest_hw IS NOT NULL", Integer.class, p)).isEqualTo(6);

        // pronostico del producto: octubre, noviembre y diciembre (estacionalidad de media 1: 120, 140, 100)
        Map<LocalDate, BigDecimal> producto = new java.util.TreeMap<>();
        jdbc.query("SELECT periodo, cantidad_proyectada FROM pronostico WHERE producto_id = ? AND variante_id IS NULL",
                rs -> {
                    producto.put(rs.getObject("periodo", LocalDate.class), rs.getBigDecimal("cantidad_proyectada"));
                }, p);
        assertThat(producto).containsOnlyKeys(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1));
        assertThat(producto.get(LocalDate.of(2026, 10, 1)).doubleValue()).isCloseTo(120.0, within(1.0));
        assertThat(producto.get(LocalDate.of(2026, 11, 1)).doubleValue()).isCloseTo(140.0, within(1.0));
        assertThat(producto.get(LocalDate.of(2026, 12, 1)).doubleValue()).isCloseTo(100.0, within(1.0));

        // desagregacion: la suma de las variantes activas es el total de cada mes; la inactiva no tiene filas
        for (Map.Entry<LocalDate, BigDecimal> mes : producto.entrySet()) {
            BigDecimal suma = jdbc.queryForObject("SELECT SUM(cantidad_proyectada) FROM pronostico WHERE producto_id = ?"
                    + " AND variante_id IS NOT NULL AND periodo = ?", BigDecimal.class, p, mes.getKey());
            assertThat(suma).isEqualByComparingTo(mes.getValue());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE variante_id = ?", Integer.class,
                inactiva)).isZero();
        BigDecimal octV1 = jdbc.queryForObject("SELECT cantidad_proyectada FROM pronostico WHERE variante_id = ?"
                + " AND periodo = '2026-10-01'", BigDecimal.class, v1);
        assertThat(octV1.doubleValue() / producto.get(LocalDate.of(2026, 10, 1)).doubleValue())
                .isCloseTo(0.7, within(0.01));

        // el resultado en memoria trae lo que B4 necesita
        assertThat(r.porVariante()).containsKeys(v1, v2).doesNotContainKey(inactiva);
        assertThat(r.porVariante().get(v1).primerMes()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(r.porVariante().get(v1).cantidades()).hasSize(3);
        assertThat(r.conHoltWinters()).isGreaterThanOrEqualTo(1);
        assertThat(r.productosPronosticados()).isEqualTo(r.conHoltWinters() + r.conSes());
        assertThat(r.wapeGlobal()).isNotNull();
    }

    @Test
    void calcularYGuardar_productoConMesEnCero_usaSesConMotivoMesEnCero() {
        // dado: la misma historia pero sin ventas en marzo de 2025
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, YearMonth.of(2025, 3));

        // cuando
        servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: HW no compite, SES con su WAPE y el motivo guardado
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT metodo, beta, wape_hw, wape_ses, motivo_sin_hw FROM analitica_producto WHERE producto_id = ?",
                p);
        assertThat(fila.get("metodo")).isEqualTo("SES");
        assertThat(fila.get("beta")).isNull();
        assertThat(fila.get("wape_hw")).isNull();
        assertThat(fila.get("wape_ses")).isNotNull();
        assertThat(fila.get("motivo_sin_hw")).isEqualTo("MES_EN_CERO");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE producto_id = ? AND metodo = 'SES'",
                Integer.class, p)).isEqualTo(9);
        // el mes en cero queda en la serie guardada con observada 0
        assertThat(jdbc.queryForObject("SELECT unidades_observadas FROM analitica_producto_mes WHERE producto_id = ?"
                + " AND periodo = '2025-03-01'", Integer.class, p)).isZero();
    }

    @Test
    void calcularYGuardar_productoConMenosDe3Meses_sinHistoriaYSinFilasDePronostico() {
        // dado: producto creado el 1 de agosto de 2026 a medianoche: solo agosto y septiembre son meses completos
        Long p = producto(LocalDateTime.of(2026, 8, 1, 0, 0), true);
        Long v = variante(p, "M", true, LocalDateTime.of(2026, 8, 1, 0, 0));
        vender(v, YearMonth.of(2026, 8), 5);
        vender(v, YearMonth.of(2026, 9), 7);

        // cuando
        ResultadoPronosticoGuardado r = servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: instantanea SIN_HISTORIA con su serie, sin pronostico y fuera de la fuente del ROP
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT metodo, meses_historia, alfa, wape_ses, unidades_observadas_12m FROM analitica_producto"
                        + " WHERE producto_id = ?", p);
        assertThat(fila.get("metodo")).isEqualTo("SIN_HISTORIA");
        assertThat(fila.get("meses_historia")).isEqualTo(2);
        assertThat(fila.get("alfa")).isNull();
        assertThat(fila.get("wape_ses")).isNull();
        assertThat(fila.get("unidades_observadas_12m")).isEqualTo(12);
        assertThat(contar("analitica_producto_mes", p)).isEqualTo(2);
        assertThat(contar("pronostico", p)).isZero();
        assertThat(r.porVariante()).doesNotContainKey(v);
    }

    @Test
    void calcularYGuardar_productoInactivo_quedaExcluido() {
        // dado: producto desactivado con 12 meses de ventas
        Long p = producto(CREACION_ANTIGUA, false);
        Long v = variante(p, "M", false, CREACION_ANTIGUA);
        for (YearMonth m = YearMonth.of(2025, 10); !m.isAfter(FIN_SERIE); m = m.plusMonths(1)) {
            vender(v, m, 10);
        }

        // cuando
        ResultadoPronosticoGuardado r = servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: ni pronostico ni instantaneas
        assertThat(contar("pronostico", p)).isZero();
        assertThat(contar("analitica_producto", p)).isZero();
        assertThat(contar("analitica_producto_mes", p)).isZero();
        assertThat(r.porVariante()).doesNotContainKey(v);
    }

    @Test
    void calcularYGuardar_segundaEjecucion_reemplazaLaAnterior() {
        // dado: una primera ejecucion con un producto
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, null);
        Long primera = ejecucion();
        servicio.calcularYGuardar(primera, CORTE);
        int filasPronostico = contar("pronostico", p);
        assertThat(filasPronostico).isEqualTo(9);

        // cuando: la segunda ejecucion (otra ejecucion, mismo corte)
        Long segunda = ejecucion();
        servicio.calcularYGuardar(segunda, CORTE);

        // entonces: mismas filas, todas de la segunda ejecucion, sin duplicados ni restos de la primera
        assertThat(contar("pronostico", p)).isEqualTo(filasPronostico);
        assertThat(contar("analitica_producto", p)).isEqualTo(1);
        assertThat(contar("analitica_producto_mes", p)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE ejecucion_id = ?", Integer.class,
                primera)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto WHERE ejecucion_id = ?",
                Integer.class, primera)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto_mes WHERE ejecucion_id = ?",
                Integer.class, primera)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE producto_id = ? AND ejecucion_id = ?",
                Integer.class, p, segunda)).isEqualTo(filasPronostico);
    }

    @Test
    void calcularYGuardar_noTocaLaVariante_versionYActualizadoEnIgual() {
        // dado
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, null);
        String sql = "SELECT version, actualizado_en, stock_fisico, stock_reservado FROM variante WHERE id = ?";
        Map<String, Object> antes1 = jdbc.queryForMap(sql, v1);
        Map<String, Object> antes2 = jdbc.queryForMap(sql, v2);

        // cuando
        servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: ni version, ni fecha de actualizacion, ni stock cambian
        assertThat(jdbc.queryForMap(sql, v1)).isEqualTo(antes1);
        assertThat(jdbc.queryForMap(sql, v2)).isEqualTo(antes2);
    }

    @Test
    void calcularYGuardar_fallaAlGuardar_nadaCambiaYSeConservaLaEjecucionAnterior() {
        // dado: una primera ejecucion guardada
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, null);
        Long primera = ejecucion();
        servicio.calcularYGuardar(primera, CORTE);
        String resumen = "SELECT COUNT(*) AS n, MIN(id) AS minimo, MAX(id) AS maximo, SUM(cantidad_proyectada) AS suma"
                + " FROM pronostico WHERE producto_id = ?";
        Map<String, Object> antes = jdbc.queryForMap(resumen, p);
        Long segunda = ejecucion();
        doThrow(new IllegalStateException("fallo simulado")).when(pronosticosEspia).saveAll(any());

        // cuando: la segunda ejecucion falla despues de borrar y guardar las instantaneas
        assertThatThrownBy(() -> servicio.calcularYGuardar(segunda, CORTE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fallo simulado");

        // entonces: rollback total, quedan las filas de la primera ejecucion y ninguna de la segunda
        assertThat(jdbc.queryForMap(resumen, p)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE ejecucion_id = ?", Integer.class,
                segunda)).isZero();
        assertThat(jdbc.queryForObject("SELECT ejecucion_id FROM analitica_producto WHERE producto_id = ?",
                Long.class, p)).isEqualTo(primera);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto_mes WHERE producto_id = ?"
                + " AND ejecucion_id = ?", Integer.class, p, primera)).isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto_mes WHERE ejecucion_id = ?",
                Integer.class, segunda)).isZero();
    }

    /** WAPE a mano desde las filas guardadas: sum |corregida - backtest del metodo elegido| / sum corregida. */
    private double wapeDesdeFilas(String filtroProducto, Object... args) {
        Map<String, Object> f = jdbc.queryForMap("SELECT SUM(ABS(m.unidades_corregidas - IF(a.metodo = 'HOLT_WINTERS',"
                + " m.backtest_hw, m.backtest_ses))) AS err_total, SUM(m.unidades_corregidas) AS real_total"
                + " FROM analitica_producto_mes m JOIN analitica_producto a ON a.producto_id = m.producto_id"
                + " WHERE a.metodo <> 'SIN_HISTORIA' AND IF(a.metodo = 'HOLT_WINTERS', m.backtest_hw,"
                + " m.backtest_ses) IS NOT NULL " + filtroProducto, args);
        return ((Number) f.get("err_total")).doubleValue() / ((Number) f.get("real_total")).doubleValue();
    }

    @Test
    void calcularYGuardar_conUnProductoHwYOtroSes_agregadosCoincidenConLoGuardado() {
        // dado: A con 36 meses (Holt-Winters) y B con 12 meses irregulares (SES con backtest)
        Long a = producto(CREACION_ANTIGUA, true);
        ventasEstacionales(variante(a, "M", true, CREACION_ANTIGUA), variante(a, "L", true, CREACION_ANTIGUA), null);
        LocalDateTime inicioB = LocalDateTime.of(2025, 10, 1, 0, 0);
        Long b = producto(inicioB, true);
        Long vb = variante(b, "M", true, inicioB);
        int[] ventasB = {40, 55, 48, 62, 51, 58, 45, 66, 53, 60, 49, 57};
        YearMonth m = YearMonth.of(2025, 10);
        for (int unidades : ventasB) {
            vender(vb, m, unidades);
            m = m.plusMonths(1);
        }

        // cuando
        ResultadoPronosticoGuardado r = servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: los conteos son los de analitica_producto (A es HW; B, SES por poca historia)
        assertThat(jdbc.queryForObject("SELECT metodo FROM analitica_producto WHERE producto_id = ?", String.class, a))
                .isEqualTo("HOLT_WINTERS");
        assertThat(jdbc.queryForMap("SELECT metodo, motivo_sin_hw FROM analitica_producto WHERE producto_id = ?", b))
                .containsEntry("metodo", "SES").containsEntry("motivo_sin_hw", "POCA_HISTORIA");
        assertThat(r.conHoltWinters()).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM analitica_producto WHERE metodo = 'HOLT_WINTERS'", Integer.class));
        assertThat(r.conSes()).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM analitica_producto WHERE metodo = 'SES'", Integer.class));
        assertThat(r.conHoltWinters()).isGreaterThanOrEqualTo(1);
        assertThat(r.conSes()).isGreaterThanOrEqualTo(1);
        assertThat(r.productosPronosticados()).isEqualTo(r.conHoltWinters() + r.conSes());

        // el WAPE de B guardado coincide con el calculado a mano desde su serie y su backtest
        double wapeB = wapeDesdeFilas("AND m.producto_id = ?", b);
        assertThat(jdbc.queryForObject("SELECT wape_ses FROM analitica_producto WHERE producto_id = ?",
                BigDecimal.class, b).doubleValue()).isCloseTo(wapeB, within(0.001));

        // WAPE global = sum errores / sum reales de los productos con backtest
        assertThat(r.wapeGlobal()).isNotNull();
        assertThat(r.wapeGlobal().doubleValue()).isCloseTo(wapeDesdeFilas(""), within(0.001));
    }

    @Test
    void calcularYGuardar_productoConSieteMeses_sesSinBacktestYWapeNulo() {
        // dado: creado el 1 de marzo de 2026: marzo a septiembre son 7 meses completos (3 a 8: SES sin backtest)
        LocalDateTime inicio = LocalDateTime.of(2026, 3, 1, 0, 0);
        Long p = producto(inicio, true);
        Long v = variante(p, "M", true, inicio);
        int[] ventas = {20, 25, 22, 30, 28, 26, 31};
        YearMonth m = YearMonth.of(2026, 3);
        for (int unidades : ventas) {
            vender(v, m, unidades);
            m = m.plusMonths(1);
        }

        // cuando
        ResultadoPronosticoGuardado r = servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: SES, sin WAPE, sin backtest en la serie y pronosticos de producto sin error
        Map<String, Object> fila = jdbc.queryForMap("SELECT metodo, meses_historia, alfa, beta, wape_ses, wape_hw,"
                + " error_wape FROM analitica_producto WHERE producto_id = ?", p);
        assertThat(fila.get("metodo")).isEqualTo("SES");
        assertThat(fila.get("meses_historia")).isEqualTo(7);
        assertThat(fila.get("alfa")).isNotNull();
        assertThat(fila.get("beta")).isNull();
        assertThat(fila.get("wape_ses")).isNull();
        assertThat(fila.get("wape_hw")).isNull();
        assertThat(fila.get("error_wape")).isNull();
        assertThat(contar("analitica_producto_mes", p)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_producto_mes WHERE producto_id = ?"
                + " AND (backtest_ses IS NOT NULL OR backtest_hw IS NOT NULL)", Integer.class, p)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE producto_id = ? AND metodo = 'SES'"
                + " AND error_wape IS NULL", Integer.class, p)).isEqualTo(6);
        assertThat(r.porVariante()).containsKey(v);
        assertThat(r.conSes()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void calcularYGuardar_ventaAnulada_noAlteraElPronosticoNiLaSerie() {
        // dado: pronostico base sin ventas anuladas
        Long p = producto(CREACION_ANTIGUA, true);
        Long v1 = variante(p, "M", true, CREACION_ANTIGUA);
        Long v2 = variante(p, "L", true, CREACION_ANTIGUA);
        ventasEstacionales(v1, v2, null);
        servicio.calcularYGuardar(ejecucion(), CORTE);
        String consulta = "SELECT variante_id, periodo, cantidad_proyectada FROM pronostico WHERE producto_id = ?"
                + " ORDER BY periodo, variante_id";
        String serie = "SELECT periodo, unidades_observadas, unidades_corregidas FROM analitica_producto_mes"
                + " WHERE producto_id = ? ORDER BY periodo";
        List<Map<String, Object>> base = jdbc.queryForList(consulta, p);
        List<Map<String, Object>> serieBase = jdbc.queryForList(serie, p);

        // cuando: aparecen ventas ANULADAS enormes (en la serie y en la ventana de mezcla) y se recalcula
        vender(v1, YearMonth.of(2026, 9), 5000, "ANULADA");
        vender(v2, YearMonth.of(2026, 5), 5000, "ANULADA");
        servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: identico (la ANULADA no cuenta ni en la serie ni en el reparto entre variantes)
        assertThat(jdbc.queryForList(consulta, p)).isEqualTo(base);
        assertThat(jdbc.queryForList(serie, p)).isEqualTo(serieBase);
    }

    @Test
    void calcularYGuardar_variantePasoQuincenaSinStock_serieMuestraObservadaYCorregida() {
        // dado: 12 meses de 50 unidades salvo agosto de 2026 (80), con la unica variante sin stock del 2 al 16 de
        // agosto (ajuste a 0 el dia 1 por la manana, entrada el dia 17): 15 de 31 dias sin stock
        LocalDateTime inicio = LocalDateTime.of(2025, 10, 1, 0, 0);
        Long p = producto(inicio, true);
        Long v = variante(p, "M", true, inicio);
        for (YearMonth m = YearMonth.of(2025, 10); !m.isAfter(FIN_SERIE); m = m.plusMonths(1)) {
            vender(v, m, m.equals(YearMonth.of(2026, 8)) ? 80 : 50);
        }
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,"
                + " reservado_despues, usuario_id, fecha, observacion) VALUES (?, 'AJUSTE_NEGATIVO', 100000, 100000, 0,"
                + " 0, 0, ?, '2026-08-01 08:00:00', 'conteo')", v, usuarioId);
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,"
                + " reservado_despues, costo_unitario, usuario_id, fecha) VALUES (?, 'ENTRADA', 100000, 0, 100000, 0,"
                + " 0, 10.00, ?, '2026-08-17 10:00:00')", v, usuarioId);

        // cuando
        servicio.calcularYGuardar(ejecucion(), CORTE);

        // entonces: agosto 80 observadas, disponibilidad 16/31 y corregida 80 / (16/31) = 155; julio sin cambio
        String sql = "SELECT unidades_observadas, disponibilidad, unidades_corregidas FROM analitica_producto_mes"
                + " WHERE producto_id = ? AND periodo = ?";
        Map<String, Object> agosto = jdbc.queryForMap(sql, p, LocalDate.of(2026, 8, 1));
        assertThat(agosto.get("unidades_observadas")).isEqualTo(80);
        assertThat(((BigDecimal) agosto.get("disponibilidad")).doubleValue()).isCloseTo(16.0 / 31, within(0.0001));
        assertThat(((BigDecimal) agosto.get("unidades_corregidas")).doubleValue()).isCloseTo(155.0, within(0.01));
        Map<String, Object> julio = jdbc.queryForMap(sql, p, LocalDate.of(2026, 7, 1));
        assertThat(((BigDecimal) julio.get("disponibilidad")).doubleValue()).isEqualTo(1.0);
        assertThat(((BigDecimal) julio.get("unidades_corregidas")).doubleValue()).isEqualTo(50.0);
        // la instantanea de 12 meses suma observadas 11 x 50 + 80 y corregidas 11 x 50 + 155
        Map<String, Object> total = jdbc.queryForMap("SELECT unidades_observadas_12m AS obs, unidades_corregidas_12m"
                + " AS cor FROM analitica_producto WHERE producto_id = ?", p);
        assertThat(total.get("obs")).isEqualTo(630);
        assertThat(((BigDecimal) total.get("cor")).doubleValue()).isCloseTo(705.0, within(0.01));
    }
}
