package co.livorno.controlstock.venta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Flujo venta, devolución y anulación contra MySQL real (B10, fase 2). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class VentaFlujoIntegracionTest {

    @Autowired VentaService ventaService;
    @Autowired DevolucionService devolucionService;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;

    Long usuarioId;
    Long productoId;
    Long varianteId;

    @BeforeEach
    void preparar() {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        Usuario u = usuarios.save(new Usuario("Admin Flujo", "flujo" + sufijo + "@livorno.test", "hash", Rol.ADMIN));
        usuarioId = u.getId();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(usuarioId, u.getEmail(), u.getNombre(), "ADMIN"), "n/a", List.of()));
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10000.00, 20000.00, NOW(6), NOW(6))", "F" + sufijo,
                categoria);
        productoId = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "F" + sufijo);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'M', 'NEGRO', 5, 0, NOW(6), NOW(6))", productoId,
                "F" + sufijo + "-M-NEG");
        varianteId = jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ?", Long.class, productoId);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM reserva WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM detalle_venta WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM alerta WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM analitica_variante WHERE variante_id = ?", varianteId);
        jdbc.update("DELETE FROM variante WHERE id = ?", varianteId);
        jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private int fisico() {
        return jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, varianteId);
    }

    private VentaDetalleResponse vender(int cantidad) {
        return ventaService.registrar(new RegistrarVentaRequest(List.of(new LineaVentaRequest(varianteId, cantidad,
                null))));
    }

    @Test
    void convertirReservasYAnular_lasReservasSiguenConvertidasYSoloSeReponeElFisico() {
        // dado: 2 unidades apartadas de las 5 físicas (reserva ACTIVA coherente con stock_reservado)
        jdbc.update("UPDATE variante SET stock_reservado = 2 WHERE id = ?", varianteId);
        jdbc.update("INSERT INTO reserva (variante_id, cantidad, cliente_nombre, cliente_contacto, creada_en, vence_en,"
                + " estado, usuario_id) VALUES (?, 2, 'Ana', '300', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 DAY),"
                + " 'ACTIVA', ?)", varianteId, usuarioId);
        Long reservaId = jdbc.queryForObject("SELECT id FROM reserva WHERE variante_id = ?", Long.class, varianteId);

        // cuando: se convierte en venta
        VentaDetalleResponse venta = ventaService.registrarDesdeReservas(
                new co.livorno.controlstock.venta.dto.ConvertirReservasRequest(List.of(reservaId), null));

        // entonces: físico 3, reservado 0, reserva CONVERTIDA con la venta y un VENTA_RESERVA
        assertThat(fisico()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class, varianteId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT venta_id FROM reserva WHERE id = ?", Long.class, reservaId))
                .isEqualTo(venta.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE tipo = 'VENTA_RESERVA' AND variante_id = ?"
                + " AND referencia_id = ?", Integer.class, varianteId, venta.id())).isEqualTo(1);
        assertThat(ventaService.obtener(venta.id()).reservas()).containsExactly(venta.reservas().get(0));

        // convertirla otra vez: 409
        assertThatThrownBy(() -> ventaService.registrarDesdeReservas(
                new co.livorno.controlstock.venta.dto.ConvertirReservasRequest(List.of(reservaId), null)))
                .isInstanceOf(ConflictoException.class);

        // anular: repone el físico y la reserva sigue CONVERTIDA (no se reactiva)
        ventaService.anular(venta.id(), new AnularVentaRequest("error de captura"));
        assertThat(fisico()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class, varianteId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reservaId))
                .isEqualTo("CONVERTIDA");
    }

    @Test
    void anular_ventaConDevoluciones_lanza422YNoCambiaStockNiEstado() {
        // dado: venta de 3 (stock 5 a 2) y una devolución no apta de 1
        VentaDetalleResponse venta = vender(3);
        Long detalle = venta.lineas().get(0).detalleId();
        devolucionService.registrar(venta.id(), new DevolucionRequest(detalle, 1, "prenda dañada", false));

        // cuando / entonces: anular se rechaza con 422 y revierte todo
        assertThatThrownBy(() -> ventaService.anular(venta.id(), new AnularVentaRequest("error de captura")))
                .isInstanceOf(ExcepcionNegocio.class)
                .hasMessage("La venta " + venta.numero() + " tiene devoluciones; corrige el stock con ajustes.");
        assertThat(fisico()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT estado FROM venta WHERE id = ?", String.class, venta.id()))
                .isEqualTo("REGISTRADA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE tipo = 'ANULACION_VENTA'"
                + " AND variante_id = ?", Integer.class, varianteId)).isZero();
    }

    @Test
    void devolver_aptaYNoApta_stockYDetalleReflejanLasDevoluciones() {
        VentaDetalleResponse venta = vender(3);
        Long detalle = venta.lineas().get(0).detalleId();

        devolucionService.registrar(venta.id(), new DevolucionRequest(detalle, 1, "talla equivocada", true));
        devolucionService.registrar(venta.id(), new DevolucionRequest(detalle, 1, "prenda dañada", false));

        // stock: 5 - 3 + 1 (solo la apta)
        assertThat(fisico()).isEqualTo(3);
        Map<String, Object> mov = jdbc.queryForMap("SELECT tipo, referencia_tipo FROM movimiento WHERE variante_id = ?"
                + " AND tipo = 'DEVOLUCION'", varianteId);
        assertThat(mov.get("referencia_tipo")).isEqualTo("DEVOLUCION");
        // detalle: cantidadDevuelta 2 y dos devoluciones con usuario
        var linea = ventaService.obtener(venta.id()).lineas().get(0);
        assertThat(linea.cantidadDevuelta()).isEqualTo(2);
        assertThat(linea.devoluciones()).hasSize(2);
        assertThat(linea.devoluciones().get(0).usuarioNombre()).isEqualTo("Admin Flujo");
        // el tope: solo queda 1 por devolver
        assertThatThrownBy(() -> devolucionService.registrar(venta.id(),
                new DevolucionRequest(detalle, 2, "otra devolución", true))).isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void anular_ventaSinDevolucionesYLuegoDevolver_restauraStockYRechazaDevolucion() {
        VentaDetalleResponse venta = vender(2);

        ventaService.anular(venta.id(), new AnularVentaRequest("error de captura"));

        assertThat(fisico()).isEqualTo(5);
        assertThatThrownBy(() -> ventaService.anular(venta.id(), new AnularVentaRequest("segunda vez")))
                .isInstanceOf(ConflictoException.class);
        Long detalle = venta.lineas().get(0).detalleId();
        assertThatThrownBy(() -> devolucionService.registrar(venta.id(),
                new DevolucionRequest(detalle, 1, "tras anulación", true))).isInstanceOf(ExcepcionNegocio.class);
        assertThat(fisico()).isEqualTo(5);
    }
}
