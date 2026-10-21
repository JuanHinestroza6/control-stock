package co.livorno.controlstock.compra.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.compra.dto.ActualizarPlanRequest;
import co.livorno.controlstock.compra.dto.CancelarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanResponse;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.PlanCompraResumenResponse;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.OrigenPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import co.livorno.controlstock.compra.repositorio.PlanCompraRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B6, fase 6: crear manual, editar BORRADOR (versión), cancelar y consultar planes de compra (ADR-010). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class PlanCompraServiceIntegracionTest {

    @Autowired PlanCompraService servicio;
    @Autowired PlanCompraRepository planes;
    @Autowired VarianteRepository variantes;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> idsVariantes = new ArrayList<>();
    private String sufijo;
    private Long usuarioId;
    private Long v1;
    private Long v2;
    private Long v3;
    private Long vInactiva;
    private Long vDeProductoInactivo;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "pc" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Compradora', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);

        Long activo = producto("A", true, "20000.00", 10);
        Long inactivo = producto("I", false, "5000.00", 10);
        v1 = variante(activo, "S", true, 7);
        v2 = variante(activo, "M", true, 0);
        v3 = variante(activo, "L", true, 0);
        vInactiva = variante(activo, "XL", false, 0);
        vDeProductoInactivo = variante(inactivo, "S", true, 0);
        como("ADMIN");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                usuarioId);
        jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
        for (Long v : idsVariantes) {
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private void como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "pc@livorno.test", "Compradora", rol);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Long producto(String marca, boolean activo, String precioCompra, int leadTime) {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = marca + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, ?, 100000.00, ?, ?, NOW(6), NOW(6))",
                cod, categoria, new BigDecimal(precioCompra), leadTime, activo);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, boolean activa, int fisico) {
        String sku = "X" + sufijo + "-" + productoId + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " activo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, 2, ?, NOW(6), NOW(6))",
                productoId, sku, talla, fisico, activa);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        return id;
    }

    private static LineaPlanRequest linea(Long varianteId, int cantidad) {
        return new LineaPlanRequest(varianteId, cantidad, null);
    }

    private PlanCompraDetalleResponse crearBasico() {
        return servicio.crear(new CrearPlanRequest("Pedido de octubre", List.of(linea(v1, 10), linea(v2, 5))));
    }

    /** Plan sugerido en BORRADOR armado a mano (origen y justificación solo los pone la sugerencia, B9). */
    private Long planSugerido() {
        PlanCompra p = PlanCompra.sugerido("Sugerido", null, LocalDateTime.now(clock), usuarioId);
        Variante a = variantes.findById(v1).orElseThrow();
        Variante b = variantes.findById(v2).orElseThrow();
        p.agregarLinea(a, 12, 10, new BigDecimal("20000.00"), "Posición 7 <= umbral 8; S = 22");
        p.agregarLinea(b, 4, 4, new BigDecimal("20000.00"), "Agotada");
        return planes.saveAndFlush(p).getId();
    }

    private Long planEnEstado(EstadoPlan estado, LocalDateTime aprobadoEn) {
        PlanCompra p = PlanCompra.manual("Plan", aprobadoEn, usuarioId);
        p.agregarLinea(variantes.findById(v1).orElseThrow(), null, 10, new BigDecimal("20000.00"), null);
        if (estado != EstadoPlan.BORRADOR) {
            p.aprobar(aprobadoEn, usuarioId);
        }
        if (estado == EstadoPlan.RECIBIDO) {
            p.recibir(aprobadoEn, usuarioId);
        }
        if (estado == EstadoPlan.CANCELADO) {
            p.cancelar("Motivo previo", aprobadoEn, usuarioId);
        }
        return planes.saveAndFlush(p).getId();
    }

    // ---------- crear ----------

    @Test
    void crear_dosLineas_quedaManualEnBorradorConCostoPorDefectoYNoTocaStock() {
        // cuando
        PlanCompraDetalleResponse r = crearBasico();

        // entonces
        assertThat(r.estado()).isEqualTo(EstadoPlan.BORRADOR);
        assertThat(r.origen()).isEqualTo(OrigenPlan.MANUAL);
        assertThat(r.numero()).isEqualTo(PlanCompra.numero(r.id()));
        assertThat(r.creadoPor()).isEqualTo("Compradora");
        assertThat(r.observacion()).isEqualTo("Pedido de octubre");
        assertThat(r.unidades()).isEqualTo(15);
        assertThat(r.costoEstimadoTotal()).isEqualByComparingTo("300000.00");
        assertThat(r.llegadaEsperada()).isNull();
        assertThat(r.atrasado()).isFalse();
        assertThat(r.omitidas()).isNull();
        assertThat(r.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(v1, v2);
        LineaPlanResponse l1 = r.lineas().get(0);
        assertThat(l1.costoEstimado()).isEqualByComparingTo("20000.00");
        assertThat(l1.subtotal()).isEqualByComparingTo("200000.00");
        assertThat(l1.cantidadSugerida()).isNull();
        assertThat(l1.disponibleActual()).isEqualTo(7);
        assertThat(l1.enCaminoActual()).isZero();
        assertThat(l1.varianteActiva()).isTrue();
        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, v1))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?", Integer.class, v1))
                .isZero();
    }

    @Test
    void crear_conCostoExplicito_usaEseCostoYElDefectoParaLasDemas() {
        PlanCompraDetalleResponse r = servicio.crear(new CrearPlanRequest("  ",
                List.of(new LineaPlanRequest(v1, 3, new BigDecimal("18500.555")), linea(v2, 2))));

        assertThat(r.observacion()).isNull();
        assertThat(r.lineas().get(0).costoEstimado()).isEqualByComparingTo("18500.56");
        assertThat(r.lineas().get(1).costoEstimado()).isEqualByComparingTo("20000.00");
    }

    @Test
    void crear_varianteRepetida_lanza400() {
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 1), linea(v1, 2)))))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("repetida");
    }

    @Test
    void crear_sinLineas_oMasDe300_lanza400() {
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of())))
                .isInstanceOf(SolicitudInvalidaException.class);
        List<LineaPlanRequest> muchas = new ArrayList<>();
        for (long i = 1; i <= 301; i++) {
            muchas.add(linea(i, 1));
        }
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, muchas)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("300");
    }

    @Test
    void crear_exactamente300Lineas_noFallaPorElLimite() {
        // 300 variantes inexistentes: pasa la validación de tamaño y falla después por 404, no por el límite
        List<LineaPlanRequest> lineas = new ArrayList<>();
        for (long i = 1; i <= 300; i++) {
            lineas.add(linea(900_000_000L + i, 1));
        }
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, lineas)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void crear_cantidadFueraDeRango_costoNegativoOObservacionLarga_lanza400() {
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 0)))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 100_001)))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null,
                List.of(new LineaPlanRequest(v1, 1, new BigDecimal("-1"))))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest("x".repeat(256), List.of(linea(v1, 1)))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void crear_cantidadMaxima100000_seAcepta() {
        PlanCompraDetalleResponse r = servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 100_000))));
        assertThat(r.unidades()).isEqualTo(100_000);
    }

    @Test
    void crear_varianteInexistente_lanza404() {
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 1), linea(999_999_999L, 1)))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void crear_varianteInactiva_lanza422ConElSku() {
        String sku = variantes.findById(vInactiva).orElseThrow().getSku();
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(vInactiva, 1)))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining(sku);
    }

    @Test
    void crear_productoInactivo_lanza422ConElSku() {
        String sku = variantes.findById(vDeProductoInactivo).orElseThrow().getSku();
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(vDeProductoInactivo, 1)))))
                .isInstanceOf(ExcepcionNegocio.class).hasMessageContaining(sku);
    }

    @Test
    void crear_fallaUnaLinea_noDejaPlanGuardado() {
        long antes = jdbc.queryForObject("SELECT COUNT(*) FROM plan_compra WHERE creado_por = ?", Long.class,
                usuarioId);
        assertThatThrownBy(() -> servicio.crear(new CrearPlanRequest(null, List.of(linea(v1, 1), linea(vInactiva, 1)))))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM plan_compra WHERE creado_por = ?", Long.class,
                usuarioId)).isEqualTo(antes);
    }

    // ---------- editar ----------

    @Test
    void actualizar_reemplazaObservacionYLineas_yAumentaLaVersion() {
        PlanCompraDetalleResponse creado = crearBasico();

        PlanCompraDetalleResponse r = servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(),
                "Nueva", List.of(linea(v1, 20), linea(v3, 2))));

        assertThat(r.observacion()).isEqualTo("Nueva");
        assertThat(r.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(v1, v3);
        assertThat(r.lineas().get(0).cantidadAprobada()).isEqualTo(20);
        assertThat(r.version()).isGreaterThan(creado.version());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM detalle_plan WHERE plan_id = ?", Integer.class,
                creado.id())).isEqualTo(2);
    }

    @Test
    void actualizar_conservaSugeridaYJustificacionDeLasVariantesQueSiguen() {
        Long id = planSugerido();
        long version = servicio.obtener(id).version();

        PlanCompraDetalleResponse r = servicio.actualizar(id, new ActualizarPlanRequest(version, null,
                List.of(linea(v1, 15), linea(v3, 3))));

        LineaPlanResponse que = r.lineas().get(0);
        assertThat(que.varianteId()).isEqualTo(v1);
        assertThat(que.cantidadSugerida()).isEqualTo(12);
        assertThat(que.cantidadAprobada()).isEqualTo(15);
        assertThat(que.justificacion()).isEqualTo("Posición 7 <= umbral 8; S = 22");
        LineaPlanResponse nueva = r.lineas().get(1);
        assertThat(nueva.varianteId()).isEqualTo(v3);
        assertThat(nueva.cantidadSugerida()).isNull();
        assertThat(nueva.justificacion()).isNull();
        assertThat(r.origen()).isEqualTo(OrigenPlan.SUGERIDO);
    }

    @Test
    void actualizar_conObservacionNull_vaciaLaObservacion() {
        // dado: un borrador con observación
        PlanCompraDetalleResponse creado = crearBasico();
        assertThat(creado.observacion()).isNotNull();

        // cuando: se guarda con observacion null
        PlanCompraDetalleResponse r = servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(),
                null, List.of(linea(v1, 10), linea(v2, 5))));

        // entonces
        assertThat(r.observacion()).isNull();
        assertThat(servicio.obtener(creado.id()).observacion()).isNull();
    }

    @Test
    void actualizar_versionVieja_lanza409YNoCambiaNada() {
        PlanCompraDetalleResponse creado = crearBasico();
        servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(), "Primera edición",
                List.of(linea(v1, 1))));

        // la segunda edición llega con la versión que leyó antes de la primera
        assertThatThrownBy(() -> servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(),
                "Segunda", List.of(linea(v2, 9)))))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("cambió");

        PlanCompraDetalleResponse actual = servicio.obtener(creado.id());
        assertThat(actual.observacion()).isEqualTo("Primera edición");
        assertThat(actual.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(v1);
    }

    @Test
    void actualizar_planNoBorrador_lanza409() {
        Long aprobado = planEnEstado(EstadoPlan.APROBADO, LocalDateTime.now(clock));
        Long cancelado = planEnEstado(EstadoPlan.CANCELADO, LocalDateTime.now(clock));
        for (Long id : List.of(aprobado, cancelado)) {
            long version = servicio.obtener(id).version();
            assertThatThrownBy(() -> servicio.actualizar(id, new ActualizarPlanRequest(version, "x",
                    List.of(linea(v1, 1)))))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining(PlanCompra.numero(id));
        }
    }

    @Test
    void actualizar_validaLasMismasReglasQueCrear() {
        PlanCompraDetalleResponse creado = crearBasico();
        long v = creado.version();
        assertThatThrownBy(() -> servicio.actualizar(creado.id(),
                new ActualizarPlanRequest(v, null, List.of(linea(v1, 1), linea(v1, 2)))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.actualizar(creado.id(),
                new ActualizarPlanRequest(v, null, List.of(linea(vInactiva, 1)))))
                .isInstanceOf(ExcepcionNegocio.class);
        assertThatThrownBy(() -> servicio.actualizar(creado.id(),
                new ActualizarPlanRequest(v, null, List.of(linea(999_999_999L, 1)))))
                .isInstanceOf(RecursoNoEncontradoException.class);
        // la falla revierte: el plan sigue con sus dos líneas originales
        assertThat(servicio.obtener(creado.id()).lineas()).hasSize(2);
    }

    @Test
    void actualizar_planInexistente_lanza404() {
        assertThatThrownBy(() -> servicio.actualizar(999_999_999L, new ActualizarPlanRequest(0L, null,
                List.of(linea(v1, 1))))).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- cancelar ----------

    @Test
    void cancelar_borrador_fijaMotivoYAutor() {
        PlanCompraDetalleResponse creado = crearBasico();

        PlanCompraDetalleResponse r = servicio.cancelar(creado.id(), new CancelarPlanRequest("  Ya no se necesita  "));

        assertThat(r.estado()).isEqualTo(EstadoPlan.CANCELADO);
        assertThat(r.motivoCancelacion()).isEqualTo("Ya no se necesita");
        assertThat(r.canceladoPor()).isEqualTo("Compradora");
        assertThat(r.canceladoEn()).isNotNull();
        assertThat(r.llegadaEsperada()).isNull();
    }

    @Test
    void cancelar_aprobadoComoAdmin_dejaDeEstarEnCamino() {
        Long id = planEnEstado(EstadoPlan.APROBADO, LocalDateTime.now(clock));
        assertThat(servicio.obtener(id).lineas().get(0).enCaminoActual()).isEqualTo(10);

        PlanCompraDetalleResponse r = servicio.cancelar(id, new CancelarPlanRequest("Proveedor sin stock"));

        assertThat(r.estado()).isEqualTo(EstadoPlan.CANCELADO);
        assertThat(r.lineas().get(0).enCaminoActual()).isZero();
    }

    @Test
    void cancelar_inventarioSobreBorrador_seAcepta() {
        PlanCompraDetalleResponse creado = crearBasico();
        como("INVENTARIO");

        assertThat(servicio.cancelar(creado.id(), new CancelarPlanRequest("Descartado")).estado())
                .isEqualTo(EstadoPlan.CANCELADO);
    }

    @Test
    void cancelar_inventarioSobreAprobado_lanzaAccesoDenegadoYNoCambiaElPlan() {
        Long id = planEnEstado(EstadoPlan.APROBADO, LocalDateTime.now(clock));
        como("INVENTARIO");

        assertThatThrownBy(() -> servicio.cancelar(id, new CancelarPlanRequest("Intento no permitido")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(servicio.obtener(id).estado()).isEqualTo(EstadoPlan.APROBADO);
    }

    @Test
    void cancelar_motivoCortoLargoOVacio_lanza400() {
        PlanCompraDetalleResponse creado = crearBasico();
        for (String motivo : new String[] {null, "", "    ", "abcd", "x".repeat(201)}) {
            assertThatThrownBy(() -> servicio.cancelar(creado.id(), new CancelarPlanRequest(motivo)))
                    .isInstanceOf(SolicitudInvalidaException.class);
        }
        assertThat(servicio.cancelar(creado.id(), new CancelarPlanRequest("x".repeat(200))).estado())
                .isEqualTo(EstadoPlan.CANCELADO);
    }

    @Test
    void cancelar_planRecibidoOCancelado_lanza409() {
        Long recibido = planEnEstado(EstadoPlan.RECIBIDO, LocalDateTime.now(clock));
        Long cancelado = planEnEstado(EstadoPlan.CANCELADO, LocalDateTime.now(clock));
        for (Long id : List.of(recibido, cancelado)) {
            assertThatThrownBy(() -> servicio.cancelar(id, new CancelarPlanRequest("Motivo válido")))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining(PlanCompra.numero(id) + " ya está");
        }
        // INVENTARIO sobre un plan ya final: 409 (no 403), el estado no es APROBADO
        como("INVENTARIO");
        assertThatThrownBy(() -> servicio.cancelar(recibido, new CancelarPlanRequest("Motivo válido")))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void cancelar_dosVecesElMismoPlan_laSegundaLanza409() {
        PlanCompraDetalleResponse creado = crearBasico();
        servicio.cancelar(creado.id(), new CancelarPlanRequest("Primera vez"));

        assertThatThrownBy(() -> servicio.cancelar(creado.id(), new CancelarPlanRequest("Segunda vez")))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("CANCELADO");
    }

    @Test
    void cancelar_planInexistente_lanza404() {
        assertThatThrownBy(() -> servicio.cancelar(999_999_999L, new CancelarPlanRequest("Motivo válido")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- consultas ----------

    @Test
    void obtener_planAprobadoAtrasado_calculaLlegadaEsperadaConElMayorLeadTime() {
        // lead time del producto = 10 días; aprobado hace 30 días
        LocalDateTime aprobadoEn = LocalDateTime.now(clock).minusDays(30);
        Long id = planEnEstado(EstadoPlan.APROBADO, aprobadoEn);

        PlanCompraDetalleResponse r = servicio.obtener(id);

        assertThat(r.llegadaEsperada()).isEqualTo(r.aprobadoEn().plusDays(10));
        assertThat(r.atrasado()).isTrue();
        assertThat(r.aprobadoPor()).isEqualTo("Compradora");
    }

    @Test
    void obtener_planAprobadoReciente_noEstaAtrasado() {
        Long id = planEnEstado(EstadoPlan.APROBADO, LocalDateTime.now(clock).minusDays(2));
        assertThat(servicio.obtener(id).atrasado()).isFalse();
    }

    @Test
    void obtener_planInexistente_lanza404() {
        assertThatThrownBy(() -> servicio.obtener(999_999_999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listar_filtraPorEstadoYPagina() {
        Long borrador = crearBasico().id();
        Long aprobado = planEnEstado(EstadoPlan.APROBADO, LocalDateTime.now(clock));
        var pagina = PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id"));

        List<PlanCompraResumenResponse> soloAprobados = servicio.listar(EstadoPlan.APROBADO, pagina).getContent();
        List<PlanCompraResumenResponse> todos = servicio.listar(null, pagina).getContent();

        assertThat(soloAprobados).extracting(PlanCompraResumenResponse::id).contains(aprobado).doesNotContain(borrador);
        assertThat(soloAprobados).allMatch(p -> p.estado() == EstadoPlan.APROBADO);
        assertThat(todos).extracting(PlanCompraResumenResponse::id).contains(borrador, aprobado);
        PlanCompraResumenResponse resumen = todos.stream().filter(p -> p.id().equals(borrador)).findFirst()
                .orElseThrow();
        assertThat(resumen.lineas()).isEqualTo(2);
        assertThat(resumen.unidades()).isEqualTo(15);
        assertThat(resumen.costoEstimadoTotal()).isEqualByComparingTo("300000.00");
        assertThat(resumen.creadoPor()).isEqualTo("Compradora");
        assertThat(resumen.llegadaEsperada()).isNull();
        // la paginación respeta el tamaño
        assertThat(servicio.listar(null, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))).getContent())
                .hasSize(1);
    }

    @Test
    void listar_ordenNoPermitido_lanza400() {
        assertThatThrownBy(() -> servicio.listar(null, PageRequest.of(0, 10, Sort.by("observacion"))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void listar_ordenPorCamposDeLaListaBlanca_seAcepta() {
        crearBasico();
        for (String campo : new String[] {"id", "creadoEn", "aprobadoEn", "estado", "origen"}) {
            assertThat(servicio.listar(null, PageRequest.of(0, 5, Sort.by(campo))).getContent()).isNotNull();
        }
    }

    // ---------- casos borde adicionales ----------

    @Test
    void crear_conCostoCero_seAceptaYNoUsaElPrecioCompra() {
        PlanCompraDetalleResponse r = servicio.crear(new CrearPlanRequest(null,
                List.of(new LineaPlanRequest(v1, 4, BigDecimal.ZERO))));

        assertThat(r.lineas().get(0).costoEstimado()).isEqualByComparingTo("0.00");
        assertThat(r.costoEstimadoTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void actualizar_mismaVarianteConOtraCantidad_noChocaConElIndiceUnico() {
        PlanCompraDetalleResponse creado = crearBasico();

        PlanCompraDetalleResponse r = servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(),
                null, List.of(linea(v1, 99), linea(v2, 5))));

        assertThat(r.lineas()).extracting(LineaPlanResponse::varianteId).containsExactly(v1, v2);
        assertThat(r.lineas().get(0).cantidadAprobada()).isEqualTo(99);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM detalle_plan WHERE plan_id = ?", Integer.class,
                creado.id())).isEqualTo(2);
    }

    @Test
    void actualizar_dosEdicionesConcurrentesConLaMismaVersion_unaGanaYLaOtraLanza409() throws Exception {
        PlanCompraDetalleResponse creado = crearBasico();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var listos = new java.util.concurrent.CountDownLatch(2);
        var salida = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Object>> futuros = new ArrayList<>();
            for (int i = 1; i <= 2; i++) {
                int cantidad = i * 10;
                futuros.add(pool.submit(() -> {
                    como("ADMIN");
                    try {
                        listos.countDown();
                        listos.await();
                        salida.await();
                        return servicio.actualizar(creado.id(), new ActualizarPlanRequest(creado.version(), null,
                                List.of(linea(v1, cantidad))));
                    } catch (Exception e) {
                        return e;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            listos.await();
            salida.countDown();
            int exitos = 0;
            int conflictos = 0;
            for (var f : futuros) {
                Object r = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (r instanceof PlanCompraDetalleResponse) {
                    exitos++;
                } else if (r instanceof ConflictoException) {
                    conflictos++;
                }
            }
            assertThat(exitos).isEqualTo(1);
            assertThat(conflictos).isEqualTo(1);
            assertThat(servicio.obtener(creado.id()).lineas()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
