package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.inventario.repositorio.AlertaRepository;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * I2, fase 5: dos evaluaciones de tallas distintas del mismo producto, con una venta reciente, cierran las
 * SIN_ROTACION del producto a la vez sin interbloquearse (ADR-009). Una barrera obliga a que ambas hayan bloqueado su
 * variante y leído las alertas antes de que cualquiera ejecute el UPDATE: con la subconsulta sobre {@code variante}
 * que se usaba antes, cada una pedía S sobre la variante que la otra tenía en X.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class SinRotacionConcurrenciaIntegracionTest {

    @Autowired AlertaService alertas;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AlertaRepository repositorioAlertas;

    private final List<Long> productosCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long p : productosCreados) {
            String hijas = "variante_id IN (SELECT id FROM variante WHERE producto_id = ?)";
            jdbc.update("DELETE FROM movimiento WHERE " + hijas, p);
            jdbc.update("DELETE FROM alerta WHERE " + hijas, p);
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        productosCreados.clear();
    }

    private static LocalDateTime hace(int dias) {
        return LocalDateTime.now(ZoneId.of("America/Bogota")).minusDays(dias);
    }

    private Long variante(Long producto, String talla) {
        String sku = "SC" + Long.toString(System.nanoTime(), 36).toUpperCase() + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado,"
                + " stock_minimo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 10, 0, 2, NOW(6), NOW(6))",
                producto, sku, talla);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    @Test
    void evaluar_dosTallasDelMismoProductoConVentaReciente_ambasCierranSinInterbloqueo() throws Exception {
        // dado: un producto viejo con dos variantes que ya tienen SIN_ROTACION ACTIVA
        String codigo = "SC" + Long.toString(System.nanoTime(), 36).toUpperCase();
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 20.00, ?, ?)", codigo, categoria, hace(100),
                hace(100));
        Long p = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
        productosCreados.add(p);
        Long a = variante(p, "M");
        Long b = variante(p, "L");
        alertas.evaluar(a);
        alertas.evaluar(b);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE tipo = 'SIN_ROTACION' AND estado = 'ACTIVA'"
                + " AND variante_id IN (?, ?)", Integer.class, a, b)).isEqualTo(2);
        // y una venta reciente de la variante A
        jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues,"
                + " reservado_antes, reservado_despues, fecha, referencia_tipo, referencia_id)"
                + " VALUES (?, 'VENTA', 1, 11, 10, 0, 0, ?, 'VENTA', 1)", a, hace(1));

        // la barrera junta a las dos evaluaciones justo después de leer las alertas y antes de cerrarlas
        CyclicBarrier barrera = new CyclicBarrier(2);
        doAnswer(invocacion -> {
            // el repositorio es una interfaz (no admite callRealMethod): se lee lo confirmado por otra conexión
            List<Long> ids = jdbc.queryForList("SELECT a.id FROM alerta a JOIN variante v ON v.id = a.variante_id"
                    + " WHERE v.producto_id = ? AND a.tipo = 'SIN_ROTACION' AND a.estado = 'ACTIVA' ORDER BY a.id",
                    Long.class, invocacion.<Long>getArgument(0));
            barrera.await(20, TimeUnit.SECONDS);
            return ids;
        }).when(repositorioAlertas).idsSinRotacionActivas(any());

        // cuando: las dos evaluaciones corren a la vez
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> fa = pool.submit(() -> alertas.evaluar(a));
            Future<?> fb = pool.submit(() -> alertas.evaluar(b));

            // entonces: ninguna falla (ni interbloqueo ni espera agotada) y las dos alertas quedan cerradas
            fa.get(40, TimeUnit.SECONDS);
            fb.get(40, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE tipo = 'SIN_ROTACION' AND estado = 'ACTIVA'"
                + " AND variante_id IN (?, ?)", Integer.class, a, b)).isZero();
    }
}
