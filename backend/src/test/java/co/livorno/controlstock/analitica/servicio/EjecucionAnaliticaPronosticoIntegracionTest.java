package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import java.time.Clock;
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
 * B4, fase 6: el orquestador con el paso de pronóstico, contra MySQL real. Las fechas son relativas a hoy (el
 * orquestador usa el {@code Clock}): 36 meses completos de demanda estacional para el producto con pronóstico y un
 * producto con un solo mes de historia (sin pronóstico, ROP histórico).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class EjecucionAnaliticaPronosticoIntegracionTest {

    /** Media 1 x 100 unidades por mes (enero a diciembre); múltiplos de 10 para repartir 7:3. */
    static final int[] UNIDADES_POR_MES = {60, 70, 80, 90, 100, 100, 110, 110, 120, 120, 140, 100};
    static final int LEAD_TIME = 10;

    @Autowired EjecucionAnaliticaService servicio;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoSpyBean PronosticoService pronostico;

    private final List<Long> productos = new ArrayList<>();
    private Long usuarioId;
    private Long categoriaId;
    private String sufijo;
    private int contador;
    private long idEjecucionBase;
    private YearMonth actual;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        actual = YearMonth.now(clock);
        String email = "ep" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Orquestador', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        categoriaId = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        idEjecucionBase = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ejecucion_analitica", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        jdbc.update("DELETE FROM analitica_variante WHERE ejecucion_id > ?", idEjecucionBase);
        jdbc.update("DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM venta WHERE usuario_id = ?)",
                usuarioId);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        for (Long p : productos) {
            jdbc.update("DELETE FROM alerta WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)", p);
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)",
                    p);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN (SELECT id FROM variante"
                    + " WHERE producto_id = ?)", p);
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id > ?", idEjecucionBase);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    // ---------- datos ----------

    private Long producto(LocalDateTime creadoEn) {
        String codigo = "EP" + sufijo + (contador++);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, ?, TRUE, ?, ?)", codigo,
                categoriaId, LEAD_TIME, creadoEn, creadoEn);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
        productos.add(id);
        return id;
    }

    /** Variante con una entrada grande al crearla: siempre con stock (disponibilidad 1). */
    private Long variante(Long productoId, String talla, LocalDateTime creadoEn) {
        String sku = "EP" + sufijo + "-" + (contador++) + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 100000, 0, TRUE, ?, ?)", productoId, sku,
                talla, creadoEn, creadoEn);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,"
                + " reservado_despues, costo_unitario, usuario_id, fecha) VALUES (?, 'ENTRADA', 100000, 0, 100000, 0, 0,"
                + " 10.00, ?, ?)", id, usuarioId, creadoEn.plusHours(1));
        return id;
    }

    private void vender(Long varianteId, YearMonth mes, int cantidad) {
        LocalDateTime fecha = mes.atDay(10).atTime(12, 0);
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado) VALUES (?, ?, 0, 'REGISTRADA')", fecha,
                usuarioId);
        Long ventaId = jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, 100.00, 10.00)", ventaId, varianteId, cantidad);
    }

    private LocalDateTime antigua() {
        return actual.minusMonths(40).atDay(1).atStartOfDay();
    }

    /** Producto con 36 meses de demanda (HW o SES según el backtest) y producto con un solo mes (SIN_HISTORIA). */
    private Long[] escenario() {
        Long conPronostico = producto(antigua());
        Long a1 = variante(conPronostico, "M", antigua());
        Long a2 = variante(conPronostico, "L", antigua());
        for (YearMonth m = actual.minusMonths(36); m.isBefore(actual); m = m.plusMonths(1)) {
            int unidades = UNIDADES_POR_MES[m.getMonthValue() - 1];
            vender(a1, m, unidades / 10 * 7);
            vender(a2, m, unidades / 10 * 3);
        }
        Long sinHistoria = producto(actual.minusMonths(2).atDay(1).atStartOfDay());
        Long b1 = variante(sinHistoria, "M", actual.minusMonths(2).atDay(1).atStartOfDay());
        vender(b1, actual.minusMonths(1), 12);
        return new Long[] {a1, a2, b1};
    }

    private Map<String, Object> instantanea(Long varianteId) {
        return jdbc.queryForMap("SELECT fuente_demanda, demanda_lead_time, tasa_pronosticada, punto_reorden,"
                + " metodo_reorden FROM analitica_variante WHERE variante_id = ?", varianteId);
    }

    // ---------- pruebas ----------

    @Test
    void ejecutar_conPronostico_ropConFuentePronosticoEnProductosPronosticadosEHistoricaEnLosDemas() {
        // dado: un producto con 36 meses de historia y otro con un mes
        Long[] v = escenario();

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.MANUAL, usuarioId);

        // entonces: exitosa, con los agregados del pronóstico registrados
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        EjecucionAnalitica guardada = ejecuciones.findById(e.getId()).orElseThrow();
        assertThat(guardada.getProductosPronosticados()).isGreaterThanOrEqualTo(1);
        assertThat(guardada.getConHoltWinters() + guardada.getConSes())
                .isEqualTo(guardada.getProductosPronosticados());
        assertThat(guardada.getVariantesAnalizadas()).isNotNull();
        assertThat(guardada.getMensaje()).isNull();

        // las variantes del producto pronosticado: fuente PRONOSTICO, μL ≈ tasa × L
        for (Long id : new Long[] {v[0], v[1]}) {
            Map<String, Object> fila = instantanea(id);
            assertThat(fila.get("fuente_demanda")).isEqualTo("PRONOSTICO");
            double mu = ((Number) fila.get("demanda_lead_time")).doubleValue();
            double tasa = ((Number) fila.get("tasa_pronosticada")).doubleValue();
            assertThat(mu).isPositive();
            assertThat(mu).isCloseTo(tasa * LEAD_TIME, within(0.01));
            assertThat(jdbc.queryForObject("SELECT punto_reorden FROM variante WHERE id = ?", Integer.class, id))
                    .isEqualTo(fila.get("punto_reorden"));
        }

        // la variante sin pronóstico: fórmula de la fase 5
        Map<String, Object> historica = instantanea(v[2]);
        assertThat(historica.get("fuente_demanda")).isEqualTo("HISTORICA");
        assertThat(historica.get("tasa_pronosticada")).isNull();
    }

    @Test
    void ejecutar_pronosticoFalla_quedaFallidaConMensajeYAplicaElRopHistorico() {
        // dado: el pronóstico falla
        Long[] v = escenario();
        doThrow(new IllegalStateException("fallo forzado con detalle interno")).when(pronostico)
                .calcularYGuardar(anyLong());

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // entonces: FALLIDA con el mensaje fijo, sin detalle interno
        EjecucionAnalitica guardada = ejecuciones.findById(e.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(guardada.getMensaje()).isEqualTo("El pronóstico falló; el ROP usó la demanda histórica")
                .doesNotContain("forzado");
        assertThat(guardada.getProductosPronosticados()).isNull();
        assertThat(guardada.getWapeGlobal()).isNull();

        // el ABC y el ROP sí se aplicaron, con la fórmula histórica y sin filas de pronóstico
        assertThat(guardada.getVariantesAnalizadas()).isGreaterThanOrEqualTo(3);
        assertThat(guardada.getAlertasEvaluadas()).isNotNull();
        assertThat(guardada.getVariantesActivas()).isNotNull();
        for (Long id : v) {
            Map<String, Object> fila = instantanea(id);
            assertThat(fila.get("fuente_demanda")).isEqualTo("HISTORICA");
            assertThat(fila.get("tasa_pronosticada")).isNull();
            assertThat(jdbc.queryForObject("SELECT punto_reorden FROM variante WHERE id = ?", Integer.class, id))
                    .isEqualTo(fila.get("punto_reorden"));
            assertThat(jdbc.queryForObject("SELECT clase_abc FROM variante WHERE id = ?", String.class, id))
                    .isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico", Integer.class)).isZero();
    }
}
