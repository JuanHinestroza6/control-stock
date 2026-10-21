package co.livorno.controlstock.compra.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.compra.dto.AprobarPlanRequest;
import co.livorno.controlstock.compra.dto.CancelarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanResponse;
import co.livorno.controlstock.compra.dto.LineaRecepcionRequest;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.RecibirPlanRequest;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.evento.PosicionCambiada;
import co.livorno.controlstock.inventario.evento.StockCambiado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B7, fase 6: aprobar y recibir planes de compra, eventos de posición y carreras de transición (ADR-010). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@RecordApplicationEvents
class PlanCompraRecepcionIntegracionTest {

    @Autowired PlanCompraService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents eventos;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> idsVariantes = new ArrayList<>();
    private String sufijo;
    private Long usuarioId;
    private Long pA;
    private Long v1;
    private Long v2;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "rc" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Compradora', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        pA = producto("A", true, "20000.00");
        v1 = variante(pA, "S", true, 7);
        v2 = variante(pA, "M", true, 0);
        como("ADMIN");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                usuarioId);
        jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
        for (Long v : idsVariantes) {
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private void como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "rc@livorno.test", "Compradora", rol);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Long producto(String marca, boolean activo, String precioCompra) {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = marca + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, ?, 100000.00, 10, ?, NOW(6), NOW(6))",
                cod, categoria, new BigDecimal(precioCompra), activo);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, boolean activa, int fisico) {
        String sku = "R" + sufijo + "-" + productoId + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " activo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, 2, ?, NOW(6), NOW(6))",
                productoId, sku, talla, fisico, activa);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        // El kardex debe explicar el stock inicial para la prueba de consistencia.
        if (fisico > 0) {
            jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues,"
                    + " reservado_antes, reservado_despues, costo_unitario, fecha) VALUES (?, 'ENTRADA', ?, 0, ?, 0, 0,"
                    + " 1.00, NOW(6))", id, fisico, fisico);
        }
        return id;
    }

    private PlanCompraDetalleResponse planBorrador() {
        return servicio.crear(new CrearPlanRequest("Pedido", List.of(new LineaPlanRequest(v1, 10, null),
                new LineaPlanRequest(v2, 5, null))));
    }

    private PlanCompraDetalleResponse planAprobado() {
        PlanCompraDetalleResponse b = planBorrador();
        return servicio.aprobar(b.id(), new AprobarPlanRequest(b.version()));
    }

    private static RecibirPlanRequest recepcion(PlanCompraDetalleResponse plan, int cant1, int cant2, String costo) {
        List<LineaRecepcionRequest> ls = new ArrayList<>();
        int i = 0;
        for (LineaPlanResponse l : plan.lineas()) {
            ls.add(new LineaRecepcionRequest(l.id(), i++ == 0 ? cant1 : cant2, new BigDecimal(costo)));
        }
        return new RecibirPlanRequest("Llegó el pedido", ls);
    }

    private int fisico(Long varianteId) {
        return jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, varianteId);
    }

    private int sumaKardex(Long varianteId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(fisico_despues - fisico_antes), 0) FROM movimiento"
                + " WHERE variante_id = ?", Integer.class, varianteId);
    }

    // ---------- aprobar ----------

    @Test
    void aprobar_borrador_quedaAprobadoPublicaPosicionPorLineaYNoTocaStock() {
        PlanCompraDetalleResponse b = planBorrador();

        PlanCompraDetalleResponse r = servicio.aprobar(b.id(), new AprobarPlanRequest(b.version()));

        assertThat(r.estado()).isEqualTo(EstadoPlan.APROBADO);
        assertThat(r.aprobadoPor()).isEqualTo("Compradora");
        assertThat(r.llegadaEsperada()).isNotNull();
        assertThat(r.lineas()).extracting(LineaPlanResponse::enCaminoActual).containsExactly(10L, 5L);
        assertThat(eventos.stream(PosicionCambiada.class).map(PosicionCambiada::varianteId).toList())
                .containsExactly(v1, v2);
        assertThat(fisico(v1)).isEqualTo(7);
    }

    @Test
    void aprobar_versionVieja_lanzaConflictoYNoPublicaEventos() {
        PlanCompraDetalleResponse b = planBorrador();

        assertThatThrownBy(() -> servicio.aprobar(b.id(), new AprobarPlanRequest(b.version() + 1)))
                .isInstanceOf(ConflictoException.class);
        assertThat(eventos.stream(PosicionCambiada.class).count()).isZero();
    }

    @Test
    void aprobar_variantePasoAInactiva_lanza422ConSku() {
        PlanCompraDetalleResponse b = planBorrador();
        jdbc.update("UPDATE variante SET activo = FALSE WHERE id = ?", v2);

        assertThatThrownBy(() -> servicio.aprobar(b.id(), new AprobarPlanRequest(b.version())))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("R" + sufijo + "-" + pA + "-M");
    }

    @Test
    void aprobar_planYaAprobado_lanza409() {
        PlanCompraDetalleResponse a = planAprobado();

        assertThatThrownBy(() -> servicio.aprobar(a.id(), new AprobarPlanRequest(a.version())))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya está APROBADO");
    }

    // ---------- recibir ----------

    @Test
    void recibir_conCostos_generaEntradasConReferenciaStockIgualKardexYActualizaPrecioCompra() {
        PlanCompraDetalleResponse a = planAprobado();

        PlanCompraDetalleResponse r = servicio.recibir(a.id(), recepcion(a, 10, 4, "21000.00"));

        assertThat(r.estado()).isEqualTo(EstadoPlan.RECIBIDO);
        assertThat(r.recibidoEn()).isNotNull();
        assertThat(r.lineas()).extracting(LineaPlanResponse::cantidadRecibida).containsExactly(10, 4);
        assertThat(r.lineas().get(0).costoRecibido()).isEqualByComparingTo("21000.00");
        assertThat(r.lineas()).extracting(LineaPlanResponse::enCaminoActual).containsExactly(0L, 0L);
        assertThat(fisico(v1)).isEqualTo(17);
        assertThat(fisico(v2)).isEqualTo(4);
        assertThat(sumaKardex(v1)).isEqualTo(17);
        assertThat(sumaKardex(v2)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE referencia_tipo = 'PLAN_COMPRA'"
                + " AND referencia_id = ? AND tipo = 'ENTRADA'", Integer.class, a.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT precio_compra FROM producto WHERE id = ?", BigDecimal.class, pA))
                .isEqualByComparingTo("21000.00");
        assertThat(eventos.stream(StockCambiado.class).map(StockCambiado::varianteId).toList())
                .containsExactlyInAnyOrder(v1, v2);
    }

    @Test
    void recibir_lineaEnCero_noGeneraMovimientoPeroQuedaRegistrada() {
        PlanCompraDetalleResponse a = planAprobado();

        PlanCompraDetalleResponse r = servicio.recibir(a.id(), recepcion(a, 6, 0, "20000.00"));

        assertThat(r.lineas()).extracting(LineaPlanResponse::cantidadRecibida).containsExactly(6, 0);
        assertThat(fisico(v1)).isEqualTo(13);
        assertThat(fisico(v2)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE referencia_tipo = 'PLAN_COMPRA'"
                + " AND referencia_id = ?", Integer.class, a.id())).isEqualTo(1);
    }

    @Test
    void recibir_todasEnCero_lanza422PidiendoCancelar() {
        PlanCompraDetalleResponse a = planAprobado();

        assertThatThrownBy(() -> servicio.recibir(a.id(), recepcion(a, 0, 0, "20000.00")))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("Cancelar");
        assertThat(servicio.obtener(a.id()).estado()).isEqualTo(EstadoPlan.APROBADO);
    }

    @Test
    void recibir_costosDistintosParaElMismoProducto_lanza422YNoMueveStock() {
        PlanCompraDetalleResponse a = planAprobado();
        List<LineaRecepcionRequest> ls = List.of(
                new LineaRecepcionRequest(a.lineas().get(0).id(), 5, new BigDecimal("21000.00")),
                new LineaRecepcionRequest(a.lineas().get(1).id(), 5, new BigDecimal("22000.00")));

        assertThatThrownBy(() -> servicio.recibir(a.id(), new RecibirPlanRequest(null, ls)))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("mismo costo");
        assertThat(fisico(v1)).isEqualTo(7);
        assertThat(servicio.obtener(a.id()).estado()).isEqualTo(EstadoPlan.APROBADO);
    }

    @Test
    void recibir_faltanLineasORepetidas_lanza400() {
        PlanCompraDetalleResponse a = planAprobado();
        Long d1 = a.lineas().get(0).id();
        var una = List.of(new LineaRecepcionRequest(d1, 5, new BigDecimal("1.00")));
        var repetida = List.of(new LineaRecepcionRequest(d1, 5, new BigDecimal("1.00")),
                new LineaRecepcionRequest(d1, 5, new BigDecimal("1.00")));

        assertThatThrownBy(() -> servicio.recibir(a.id(), new RecibirPlanRequest(null, una)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.recibir(a.id(), new RecibirPlanRequest(null, repetida)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void recibir_varianteDesactivadaDespuesDeAprobar_seRecibeIgual() {
        PlanCompraDetalleResponse a = planAprobado();
        jdbc.update("UPDATE variante SET activo = FALSE WHERE id = ?", v2);

        PlanCompraDetalleResponse r = servicio.recibir(a.id(), recepcion(a, 3, 5, "20000.00"));

        assertThat(r.estado()).isEqualTo(EstadoPlan.RECIBIDO);
        assertThat(fisico(v2)).isEqualTo(5);
        assertThat(sumaKardex(v2)).isEqualTo(5);
    }

    @Test
    void recibir_planEnBorradorOYaRecibido_lanza409() {
        PlanCompraDetalleResponse b = planBorrador();
        assertThatThrownBy(() -> servicio.recibir(b.id(), recepcion(b, 1, 1, "1.00")))
                .isInstanceOf(ConflictoException.class);

        PlanCompraDetalleResponse a = planAprobado();
        servicio.recibir(a.id(), recepcion(a, 1, 1, "1.00"));
        assertThatThrownBy(() -> servicio.recibir(a.id(), recepcion(a, 1, 1, "1.00")))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya está RECIBIDO");
        assertThat(fisico(v1)).isEqualTo(8);
    }

    @Test
    void aprobar_productoPasoAInactivo_lanza422() {
        PlanCompraDetalleResponse b = planBorrador();
        jdbc.update("UPDATE producto SET activo = FALSE WHERE id = ?", pA);

        assertThatThrownBy(() -> servicio.aprobar(b.id(), new AprobarPlanRequest(b.version())))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining("inactiva");
        assertThat(servicio.obtener(b.id()).estado()).isEqualTo(EstadoPlan.BORRADOR);
    }

    @Test
    void recibir_costoCero_seGuardaEnLaLineaPeroNoActualizaPrecioCompra() {
        PlanCompraDetalleResponse a = planAprobado();

        PlanCompraDetalleResponse r = servicio.recibir(a.id(), recepcion(a, 2, 3, "0.00"));

        assertThat(r.estado()).isEqualTo(EstadoPlan.RECIBIDO);
        assertThat(r.lineas().get(0).costoRecibido()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT precio_compra FROM producto WHERE id = ?", BigDecimal.class, pA))
                .isEqualByComparingTo("20000.00");
        assertThat(fisico(v1)).isEqualTo(9);
    }

    @Test
    void recibir_variasVariantes_registraEntradasEnOrdenDeIdYConElUsuarioQueRecibe() {
        PlanCompraDetalleResponse a = planAprobado();
        // Líneas informadas en orden inverso al de los ids
        List<LineaRecepcionRequest> inversa = new ArrayList<>(recepcion(a, 4, 5, "20000.00").lineas());
        java.util.Collections.reverse(inversa);

        servicio.recibir(a.id(), new RecibirPlanRequest(null, inversa));

        List<Long> orden = jdbc.queryForList("SELECT variante_id FROM movimiento WHERE referencia_tipo = 'PLAN_COMPRA'"
                + " AND referencia_id = ? ORDER BY id", Long.class, a.id());
        assertThat(orden).containsExactly(v1, v2);
        assertThat(jdbc.queryForList("SELECT DISTINCT usuario_id FROM movimiento WHERE referencia_tipo = 'PLAN_COMPRA'"
                + " AND referencia_id = ?", Long.class, a.id())).containsExactly(usuarioId);
    }

    @Test
    void recibir_cantidadMayorALaAprobada_seAceptaYQuedaRegistrada() {
        PlanCompraDetalleResponse a = planAprobado();

        PlanCompraDetalleResponse r = servicio.recibir(a.id(), recepcion(a, 15, 5, "20000.00"));

        assertThat(r.lineas()).extracting(LineaPlanResponse::cantidadRecibida).containsExactly(15, 5);
        assertThat(fisico(v1)).isEqualTo(22);
        assertThat(sumaKardex(v1)).isEqualTo(22);
    }

    @Test
    void recibir_cantidadFueraDeRango_lanza400() {
        PlanCompraDetalleResponse a = planAprobado();

        assertThatThrownBy(() -> servicio.recibir(a.id(), recepcion(a, 100001, 1, "1.00")))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.recibir(a.id(), recepcion(a, -1, 1, "1.00")))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(fisico(v1)).isEqualTo(7);
    }

    @Test
    void recibir_detalleDeOtroPlan_lanza400() {
        PlanCompraDetalleResponse a = planAprobado();
        PlanCompraDetalleResponse otro = servicio.crear(new CrearPlanRequest("Otro",
                List.of(new LineaPlanRequest(v1, 1, null), new LineaPlanRequest(v2, 1, null))));
        List<LineaRecepcionRequest> ajenas = List.of(
                new LineaRecepcionRequest(otro.lineas().get(0).id(), 1, new BigDecimal("1.00")),
                new LineaRecepcionRequest(a.lineas().get(1).id(), 1, new BigDecimal("1.00")));

        assertThatThrownBy(() -> servicio.recibir(a.id(), new RecibirPlanRequest(null, ajenas)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void aprobarYActualizarConcurrentes_exactamenteUnoGana() throws Exception {
        PlanCompraDetalleResponse b = planBorrador();
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int exitos = 0;
        try {
            Future<Object> aprobar = pool.submit(tarea(salida,
                    () -> servicio.aprobar(b.id(), new AprobarPlanRequest(b.version()))));
            Future<Object> editar = pool.submit(tarea(salida, () -> servicio.actualizar(b.id(),
                    new co.livorno.controlstock.compra.dto.ActualizarPlanRequest(b.version(), "Editado",
                            List.of(new LineaPlanRequest(v1, 20, null))))));
            salida.countDown();
            for (Future<Object> f : List.of(aprobar, editar)) {
                try {
                    f.get();
                    exitos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ConflictoException.class);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(exitos).isEqualTo(1);
        PlanCompraDetalleResponse fin = servicio.obtener(b.id());
        if (fin.estado() == EstadoPlan.APROBADO) {
            assertThat(fin.lineas()).hasSize(2);
        } else {
            assertThat(fin.estado()).isEqualTo(EstadoPlan.BORRADOR);
            assertThat(fin.lineas()).hasSize(1);
        }
    }

    // ---------- cancelar ----------

    @Test
    void cancelar_aprobado_publicaPosicionPorLineaYBorradorNo() {
        PlanCompraDetalleResponse a = planAprobado();
        eventos.clear();

        servicio.cancelar(a.id(), new CancelarPlanRequest("Proveedor sin stock"));

        assertThat(eventos.stream(PosicionCambiada.class).map(PosicionCambiada::varianteId).toList())
                .containsExactly(v1, v2);

        PlanCompraDetalleResponse b = planBorrador();
        eventos.clear();
        servicio.cancelar(b.id(), new CancelarPlanRequest("Ya no hace falta"));
        assertThat(eventos.stream(PosicionCambiada.class).count()).isZero();
    }

    // ---------- concurrencia ----------

    @Test
    void recibirYCancelarConcurrentes_exactamenteUnaTransicion() throws Exception {
        PlanCompraDetalleResponse a = planAprobado();
        var recepcion = recepcion(a, 10, 5, "20000.00");
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> recibir = pool.submit(tarea(salida, () -> servicio.recibir(a.id(), recepcion)));
            Future<Object> cancelar = pool.submit(tarea(salida,
                    () -> servicio.cancelar(a.id(), new CancelarPlanRequest("Cancelación concurrente"))));
            salida.countDown();
            int exitos = 0;
            int conflictos = 0;
            for (Future<Object> f : List.of(recibir, cancelar)) {
                try {
                    f.get();
                    exitos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(ConflictoException.class);
                    conflictos++;
                }
            }
            assertThat(exitos).isEqualTo(1);
            assertThat(conflictos).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        EstadoPlan estado = servicio.obtener(a.id()).estado();
        int entradas = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE referencia_tipo = 'PLAN_COMPRA'"
                + " AND referencia_id = ?", Integer.class, a.id());
        if (estado == EstadoPlan.RECIBIDO) {
            assertThat(entradas).isEqualTo(2);
            assertThat(fisico(v1)).isEqualTo(17);
        } else {
            assertThat(estado).isEqualTo(EstadoPlan.CANCELADO);
            assertThat(entradas).isZero();
            assertThat(fisico(v1)).isEqualTo(7);
        }
        assertThat(sumaKardex(v1)).isEqualTo(fisico(v1));
    }

    private Callable<Object> tarea(CountDownLatch salida, Callable<Object> accion) {
        return () -> {
            como("ADMIN");
            try {
                salida.await();
                return accion.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
