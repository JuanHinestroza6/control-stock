package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.config.ConfiguracionTareas;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.LineaReservaRequest;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.inventario.servicio.VencimientoReservasTarea;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Vencimiento automático contra MySQL real (B3, fase 3). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class VencimientoReservasIntegracionTest {

    @Autowired ReservaService reservaService;
    @Autowired VencimientoReservasTarea tarea;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext contexto;

    Long usuarioId;
    Long productoId;
    Long varianteId;

    @BeforeEach
    void preparar() {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        Usuario u = usuarios.save(new Usuario("Vend Venc", "venc" + sufijo + "@livorno.test", "hash", Rol.VENDEDOR));
        usuarioId = u.getId();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(usuarioId, u.getEmail(), u.getNombre(), "VENDEDOR"), "n/a", List.of()));
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10000.00, 20000.00, NOW(6), NOW(6))", "V" + sufijo,
                categoria);
        productoId = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "V" + sufijo);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'M', 'NEGRO', 5, 0, NOW(6), NOW(6))", productoId,
                "V" + sufijo + "-M-NEG");
        varianteId = jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ?", Long.class, productoId);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM alerta WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM reserva WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM variante WHERE id = ?", varianteId);
        jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private int reservado() {
        return jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class, varianteId);
    }

    private ReservaResponse reservar(int cantidad, Integer vigenciaHoras) {
        return reservaService.crear(new CrearReservaRequest("Ana Pérez", "3001234567", vigenciaHoras,
                List.of(new LineaReservaRequest(varianteId, cantidad)))).get(0);
    }

    @Test
    void procesar_reservaVencida_liberaStockPorElSistemaYEsIdempotente() {
        // dado: una reserva de 2 (reservado 2) que ya pasó su vigencia
        ReservaResponse r = reservar(2, 1);
        assertThat(reservado()).isEqualTo(2);
        LocalDateTime despues = r.venceEn().plusMinutes(5);

        // cuando: corre la tarea dos veces
        int primera = tarea.procesar(despues);
        int segunda = tarea.procesar(despues);

        // entonces: se vence una sola vez, con LIBERACION del sistema
        assertThat(primera).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(reservado()).isZero();
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, cerrada_por, cerrada_en FROM reserva WHERE id = ?", r.id());
        assertThat(fila.get("estado")).isEqualTo("VENCIDA");
        assertThat(fila.get("cerrada_por")).isNull();
        assertThat(fila.get("cerrada_en")).isNotNull();
        List<Map<String, Object>> liberaciones = jdbc.queryForList(
                "SELECT usuario_id, referencia_tipo, referencia_id, observacion FROM movimiento"
                        + " WHERE variante_id = ? AND tipo = 'LIBERACION'", varianteId);
        assertThat(liberaciones).hasSize(1);
        assertThat(liberaciones.get(0).get("usuario_id")).isNull();
        assertThat(liberaciones.get(0).get("referencia_tipo")).isEqualTo("RESERVA");
        assertThat(((Number) liberaciones.get(0).get("referencia_id")).longValue()).isEqualTo(r.id());
        assertThat(liberaciones.get(0).get("observacion")).isEqualTo("Vencimiento automático");
    }

    @Test
    void procesar_reservaVigente_noSeToca() {
        ReservaResponse r = reservar(2, 48);

        int vencidas = tarea.procesar(r.venceEn().minusHours(1));

        assertThat(vencidas).isZero();
        assertThat(reservado()).isEqualTo(2);
    }

    @Test
    void vencer_reservaYaCancelada_seOmiteSinMovimiento() {
        ReservaResponse r = reservar(2, 1);
        reservaService.cancelar(r.id(), new co.livorno.controlstock.inventario.dto.CancelarReservaRequest(
                "Cliente desistió"));

        boolean vencio = reservaService.vencer(r.id(), r.venceEn().plusHours(1));

        assertThat(vencio).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ? AND tipo = 'LIBERACION'",
                Integer.class, varianteId)).isEqualTo(1);
    }

    @Test
    void tareasProgramadas_enPruebas_estanApagadas() {
        assertThat(contexto.getBeanNamesForType(ConfiguracionTareas.class)).isEmpty();
    }
}
