package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** B5, fase 5: orquestador de la ejecución analítica contra MySQL real. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class EjecucionAnaliticaServiceIntegracionTest {

    @Autowired EjecucionAnaliticaService servicio;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean CalculoAnaliticoService calculo;
    @MockitoSpyBean AlertaService alertas;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> variantesCreadas = new ArrayList<>();
    private long idEjecucionBase;
    private Long usuarioId;
    private String sufijo;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "eje" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Ejecucion', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        idEjecucionBase = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ejecucion_analitica", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        jdbc.update("DELETE FROM analitica_variante WHERE ejecucion_id > ?", idEjecucionBase);
        for (Long v : variantesCreadas) {
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM reserva WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id > ?", idEjecucionBase);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long producto() {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = "E" + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, 10, TRUE, NOW(6), NOW(6))",
                cod, categoria);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    /** Variante activa sin stock (AGOTADO) creada hace mucho tiempo. */
    private Long variante(Long productoId, String talla) {
        String sku = "E" + sufijo + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, TRUE, ?, NOW(6))", productoId, sku, talla,
                LocalDateTime.of(2020, 1, 1, 8, 0));
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        variantesCreadas.add(id);
        return id;
    }

    private int alertasActivas(Long varianteId, String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE variante_id = ? AND tipo = ? AND"
                + " estado = 'ACTIVA'", Integer.class, varianteId, tipo);
    }

    @Test
    void ejecutar_nocturna_terminaExitosaConConteosYMetricaIgualAlConteoSql() {
        // dado: dos variantes agotadas
        Long p = producto();
        Long v1 = variante(p, "S");
        Long v2 = variante(p, "M");

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // entonces
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        assertThat(e.getOrigen()).isEqualTo(OrigenEjecucion.NOCTURNA);
        assertThat(e.getTerminadaEn()).isNotNull();
        assertThat(e.getDuracionMs()).isNotNegative();
        assertThat(e.getFechaCorte()).isEqualTo(LocalDate.now());
        assertThat(alertasActivas(v1, "AGOTADO")).isEqualTo(1);
        assertThat(alertasActivas(v2, "AGOTADO")).isEqualTo(1);
        int totalVariantes = jdbc.queryForObject("SELECT COUNT(*) FROM variante", Integer.class);
        assertThat(e.getAlertasEvaluadas()).isEqualTo(totalVariantes);
        assertThat(e.getAlertasFallidas()).isZero();
        assertThat(e.getVariantesAnalizadas()).isGreaterThanOrEqualTo(2);
        assertThat(e.getClaseA() + e.getClaseB() + e.getClaseC()).isEqualTo(e.getVariantesAnalizadas());

        // métrica = conteo SQL independiente
        int activas = jdbc.queryForObject("SELECT COUNT(*) FROM variante v, producto p WHERE p.id = v.producto_id"
                + " AND v.activo = 1 AND p.activo = 1", Integer.class);
        int enAlerta = jdbc.queryForObject("SELECT COUNT(*) FROM variante v, producto p WHERE p.id = v.producto_id"
                + " AND v.activo = 1 AND p.activo = 1 AND EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id"
                + " AND a.estado = 'ACTIVA' AND a.tipo IN ('AGOTADO','STOCK_BAJO'))", Integer.class);
        assertThat(e.getVariantesActivas()).isEqualTo(activas);
        assertThat(e.getEnAlertaStock()).isEqualTo(enAlerta);
        assertThat(e.getPorcentajeAlertaStock()).isEqualByComparingTo(
                BigDecimal.valueOf(enAlerta * 100.0 / activas).setScale(1, java.math.RoundingMode.HALF_UP));

        // persistida
        assertThat(ejecuciones.findById(e.getId()).orElseThrow().getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
    }

    @Test
    void ejecutar_manual_guardaElUsuario() {
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.MANUAL, usuarioId);

        assertThat(e.getOrigen()).isEqualTo(OrigenEjecucion.MANUAL);
        assertThat(e.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
    }

    @Test
    void ejecutar_falloEnElCalculo_quedaFallidaSinTocarValoresYReevaluaAlertas() {
        // dado: una variante con valores previos y un cálculo que falla
        Long p = producto();
        Long v = variante(p, "S");
        jdbc.update("UPDATE variante SET punto_reorden = 7, clase_abc = 'B' WHERE id = ?", v);
        doThrow(new IllegalStateException("fallo forzado con detalle interno")).when(calculo).calcularYAplicar(anyLong(), anyMap());

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // entonces
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(e.getMensaje()).isEqualTo(EjecucionAnaliticaService.MENSAJE_CALCULO_FALLIDO)
                .doesNotContain("forzado");
        assertThat(jdbc.queryForObject("SELECT punto_reorden FROM variante WHERE id = ?", Integer.class, v))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT clase_abc FROM variante WHERE id = ?", String.class, v)).isEqualTo("B");
        assertThat(alertasActivas(v, "AGOTADO")).isEqualTo(1);

        // la métrica y las alertas sí se guardan (= conteo SQL); los conteos del cálculo quedan null
        int activas = jdbc.queryForObject("SELECT COUNT(*) FROM variante v, producto p WHERE p.id = v.producto_id"
                + " AND v.activo = 1 AND p.activo = 1", Integer.class);
        int enAlerta = jdbc.queryForObject("SELECT COUNT(*) FROM variante v, producto p WHERE p.id = v.producto_id"
                + " AND v.activo = 1 AND p.activo = 1 AND EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id"
                + " AND a.estado = 'ACTIVA' AND a.tipo IN ('AGOTADO','STOCK_BAJO'))", Integer.class);
        EjecucionAnalitica guardada = ejecuciones.findById(e.getId()).orElseThrow();
        assertThat(guardada.getVariantesActivas()).isEqualTo(activas);
        assertThat(guardada.getEnAlertaStock()).isEqualTo(enAlerta);
        assertThat(guardada.getPorcentajeAlertaStock()).isNotNull();
        assertThat(guardada.getAlertasEvaluadas())
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM variante", Integer.class));
        assertThat(guardada.getAlertasFallidas()).isZero();
        assertThat(guardada.getSinRotacionActivas()).isNotNull();
        assertThat(guardada.getVariantesAnalizadas()).isNull();
        assertThat(guardada.getClaseA()).isNull();
        assertThat(guardada.getConPuntoReorden()).isNull();
    }

    @Test
    void ejecutar_falloEvaluandoUnaVariante_noDetieneElResto() {
        // dado: la primera variante falla al evaluarse
        Long p = producto();
        Long v1 = variante(p, "S");
        Long v2 = variante(p, "M");
        doThrow(new IllegalStateException("boom")).when(alertas).evaluar(org.mockito.ArgumentMatchers.eq(v1), org.mockito.ArgumentMatchers.anyLong());

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // entonces
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        assertThat(e.getAlertasFallidas()).isEqualTo(1);
        assertThat(alertasActivas(v1, "AGOTADO")).isZero();
        assertThat(alertasActivas(v2, "AGOTADO")).isEqualTo(1);
    }

    @Test
    void ejecutar_ejecucionEnCursoHuerfana_quedaFallidaInterrumpida() {
        // dado: una EN_CURSO que quedó de una caída
        EjecucionAnalitica huerfana = ejecuciones.save(EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null,
                LocalDateTime.now().minusHours(3), LocalDate.now()));

        // cuando
        EjecucionAnalitica e = servicio.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // entonces
        EjecucionAnalitica recargada = ejecuciones.findById(huerfana.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoEjecucion.FALLIDA);
        assertThat(recargada.getMensaje()).contains("interrumpida");
        assertThat(e.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        assertThat(ejecuciones.findByEstado(EstadoEjecucion.EN_CURSO)).isEmpty();
    }

    @Test
    void ejecutar_segundaLlamadaConcurrente_seRechazaYLaGuardiaSeLibera() throws Exception {
        // dado: una ejecución detenida dentro del cálculo
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        doAnswer(inv -> {
            dentro.countDown();
            soltar.await(30, TimeUnit.SECONDS);
            return inv.callRealMethod();
        }).when(calculo).calcularYAplicar(anyLong(), anyMap());
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            Future<EjecucionAnalitica> primera = hilo.submit(() -> servicio.ejecutar(OrigenEjecucion.NOCTURNA, null));
            assertThat(dentro.await(30, TimeUnit.SECONDS)).isTrue();

            // cuando / entonces: la manual y la nocturna chocan
            assertThatThrownBy(() -> servicio.ejecutar(OrigenEjecucion.MANUAL, usuarioId))
                    .isInstanceOf(EjecucionEnCursoException.class).hasMessage("Ya hay un recálculo en curso.");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ejecucion_analitica WHERE id > ?", Integer.class,
                    idEjecucionBase)).isEqualTo(1);

            soltar.countDown();
            assertThat(primera.get(60, TimeUnit.SECONDS).getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        } finally {
            soltar.countDown();
            hilo.shutdownNow();
        }

        // la guardia se liberó: una nueva ejecución pasa
        assertThat(servicio.ejecutar(OrigenEjecucion.MANUAL, usuarioId).getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
    }
}
