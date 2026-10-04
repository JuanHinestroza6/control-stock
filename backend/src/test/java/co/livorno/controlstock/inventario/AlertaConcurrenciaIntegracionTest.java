package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Una salida y una entrada simultáneas nunca dejan alertas ACTIVAS incoherentes con el stock final. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class AlertaConcurrenciaIntegracionTest {

    private static final int RONDAS = 20;

    @Autowired MovimientoService movimientos;
    @Autowired JdbcTemplate jdbc;

    private Long producto;
    private Long variante;

    @AfterEach
    void limpiar() {
        if (variante != null) {
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", variante);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", variante);
            jdbc.update("DELETE FROM variante WHERE id = ?", variante);
            jdbc.update("DELETE FROM producto WHERE id = ?", producto);
        }
    }

    private void crearVariante(int fisico, int stockMinimo) {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 20.00, NOW(6), NOW(6))", "A" + sufijo, categoria);
        producto = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "A" + sufijo);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado,"
                + " stock_minimo, creado_en, actualizado_en) VALUES (?, ?, 'M', 'NEGRO', ?, 0, ?, NOW(6), NOW(6))",
                producto, "A" + sufijo + "-M-NEG", fisico, stockMinimo);
        variante = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class,
                "A" + sufijo + "-M-NEG");
    }

    @Test
    void evaluar_salidaYEntradaSimultaneas_dejanComoMuchoUnaAlertaActivaCoherenteConElStock() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < RONDAS; ronda++) {
                limpiar();
                crearVariante(1, 2);
                CountDownLatch salida = new CountDownLatch(1);
                Future<?> venta = pool.submit(() -> {
                    esperar(salida);
                    intentar(new SolicitudMovimiento(variante, TipoMovimiento.VENTA, 1, null, null, null, null,
                            null));
                });
                Future<?> entrada = pool.submit(() -> {
                    esperar(salida);
                    intentar(new SolicitudMovimiento(variante, TipoMovimiento.ENTRADA, 10,
                            new BigDecimal("10.00"), null, null, null, null));
                });
                salida.countDown();
                venta.get(60, TimeUnit.SECONDS);
                entrada.get(60, TimeUnit.SECONDS);

                Map<String, Object> v = jdbc.queryForMap(
                        "SELECT stock_fisico - stock_reservado AS disponible FROM variante WHERE id = ?", variante);
                int disponible = ((Number) v.get("disponible")).intValue();
                List<String> activas = jdbc.queryForList(
                        "SELECT tipo FROM alerta WHERE variante_id = ? AND estado = 'ACTIVA'", String.class,
                        variante);
                String esperada = disponible == 0 ? "AGOTADO" : disponible <= 2 ? "STOCK_BAJO" : null;
                assertThat(activas).as("ronda %d, disponible %d", ronda, disponible)
                        .containsExactlyInAnyOrderElementsOf(esperada == null ? List.of() : List.of(esperada));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Un choque optimista (409) de una de las dos operaciones es válido; el estado final debe seguir coherente. */
    private void intentar(SolicitudMovimiento solicitud) {
        try {
            movimientos.registrar(solicitud);
        } catch (RuntimeException e) {
            // choque de concurrencia esperado: la operación perdedora no se aplica
        }
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
