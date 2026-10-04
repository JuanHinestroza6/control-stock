package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.inventario.servicio.AnonimizacionReservasTarea;
import java.time.LocalDateTime;
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

/** Anonimización de reservas cerradas contra MySQL real (Ley 1581, ADR-007). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class AnonimizacionReservasIntegracionTest {

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Autowired AnonimizacionReservasTarea tarea;
    @Autowired JdbcTemplate jdbc;

    Long usuarioId;
    Long productoId;
    Long varianteId;

    @BeforeEach
    void preparar() {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "anon" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, creado_en, actualizado_en)"
                + " VALUES ('Vend Anon', ?, 'hash', 'VENDEDOR', 1, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10000.00, 20000.00, NOW(6), NOW(6))", "A" + sufijo,
                categoria);
        productoId = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "A" + sufijo);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'M', 'NEGRO', 5, 1, NOW(6), NOW(6))", productoId,
                "A" + sufijo + "-M-NEG");
        varianteId = jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ?", Long.class, productoId);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reserva WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM variante WHERE id = ?", varianteId);
        jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private long insertar(String estado, LocalDateTime cerradaEn) {
        LocalDateTime creada = AHORA.minusDays(200);
        jdbc.update("INSERT INTO reserva (variante_id, cantidad, cliente_nombre, cliente_contacto, creada_en,"
                + " vence_en, estado, usuario_id, cerrada_en, motivo_cancelacion) VALUES (?, 1, 'Ana Perez',"
                + " '3001234567', ?, ?, ?, ?, ?, ?)", varianteId, creada, creada.plusHours(48), estado, usuarioId,
                cerradaEn, "CANCELADA".equals(estado) ? "Cliente desistio" : null);
        return jdbc.queryForObject("SELECT MAX(id) FROM reserva WHERE variante_id = ?", Long.class, varianteId);
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT cliente_nombre, cliente_contacto, estado FROM reserva WHERE id = ?", id);
    }

    @Test
    void procesar_reservasCerradasAntiguas_seAnonimizanYLasDemasNoSeTocan() {
        // dado: cerradas hace 91 y 90 dias (se anonimizan), hace 90 dias menos 1 s (no) y una ACTIVA
        long vencida = insertar("VENCIDA", AHORA.minusDays(91));
        long cancelada = insertar("CANCELADA", AHORA.minusDays(90));
        long reciente = insertar("VENCIDA", AHORA.minusDays(90).plusSeconds(1));
        long activa = insertar("ACTIVA", null);
        int reservadoAntes = jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?",
                Integer.class, varianteId);
        int movimientosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?",
                Integer.class, varianteId);

        // cuando: corre la tarea dos veces
        int primera = tarea.procesar(AHORA);
        int segunda = tarea.procesar(AHORA);

        // entonces: solo las dos antiguas, una sola vez; estado intacto; sin movimientos ni cambios de stock
        assertThat(primera).isGreaterThanOrEqualTo(2);
        assertThat(segunda).isZero();
        assertThat(fila(vencida)).containsEntry("cliente_nombre", "[anonimizado]")
                .containsEntry("cliente_contacto", "[anonimizado]").containsEntry("estado", "VENCIDA");
        assertThat(fila(cancelada)).containsEntry("cliente_nombre", "[anonimizado]")
                .containsEntry("estado", "CANCELADA");
        assertThat(fila(reciente)).containsEntry("cliente_nombre", "Ana Perez");
        assertThat(fila(activa)).containsEntry("cliente_nombre", "Ana Perez")
                .containsEntry("cliente_contacto", "3001234567");
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class,
                varianteId)).isEqualTo(reservadoAntes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?", Integer.class,
                varianteId)).isEqualTo(movimientosAntes);
    }
}
