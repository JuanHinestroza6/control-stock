package co.livorno.controlstock.compra.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.servicio.ConsultaAnaliticaService;
import co.livorno.controlstock.analitica.servicio.EjecucionAnaliticaService;
import co.livorno.controlstock.analitica.dto.ReordenVarianteResponse;
import co.livorno.controlstock.compra.dto.AprobarPlanRequest;
import co.livorno.controlstock.compra.dto.CancelarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanRequest;
import co.livorno.controlstock.compra.dto.LineaPlanResponse;
import co.livorno.controlstock.compra.dto.LineaRecepcionRequest;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.RecibirPlanRequest;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.InventarioItemResponse;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import co.livorno.controlstock.inventario.servicio.InventarioService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B8, fase 6: alertas por posición de inventario (disponible + en camino, ADR-010), eventos de posición, métrica
 * contrafactual y {@code enCamino} en inventario y reorden. Usa los servicios reales y el listener AFTER_COMMIT.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class PlanCompraAlertasIntegracionTest {

    @Autowired PlanCompraService planes;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AlertaService alertas;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventarioService inventario;
    @Autowired ConsultaAnaliticaService consultaAnalitica;
    @Autowired EjecucionAnaliticaService ejecucion;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> idsVariantes = new ArrayList<>();
    private String sufijo;
    private Long usuarioId;
    private Long pA;
    /** Disponible 3, stock mínimo 5: STOCK_BAJO sin pedido. */
    private Long bajo;
    /** Disponible 0: AGOTADO. */
    private Long agotado;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "al" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Compradora', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        pA = producto();
        bajo = variante(pA, "S", 3);
        agotado = variante(pA, "M", 0);
        como("ADMIN");
        // Las inserciones por SQL no publican eventos: se evalúa la situación inicial.
        alertas.evaluar(bajo);
        alertas.evaluar(agotado);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                usuarioId);
        jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
        for (Long v : idsVariantes) {
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM pronostico WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM pronostico WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM analitica_producto_mes WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM analitica_producto WHERE producto_id = ?", p);
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private void como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "al@livorno.test", "Compradora", rol);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    private Long producto() {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = "AL" + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 20000.00, 100000.00, 10, TRUE, NOW(6),"
                + " NOW(6))", cod, categoria);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, int fisico) {
        String sku = "AL" + sufijo + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " activo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, 5, TRUE, NOW(6), NOW(6))",
                productoId, sku, talla, fisico);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        idsVariantes.add(id);
        if (fisico > 0) {
            jdbc.update("INSERT INTO movimiento (variante_id, tipo, cantidad, fisico_antes, fisico_despues,"
                    + " reservado_antes, reservado_despues, costo_unitario, fecha) VALUES (?, 'ENTRADA', ?, 0, ?, 0, 0,"
                    + " 1.00, NOW(6))", id, fisico, fisico);
        }
        return id;
    }

    private PlanCompraDetalleResponse planAprobado(int cantidadBajo) {
        PlanCompraDetalleResponse b = planes.crear(new CrearPlanRequest("Pedido",
                List.of(new LineaPlanRequest(bajo, cantidadBajo, null), new LineaPlanRequest(agotado, 5, null))));
        return planes.aprobar(b.id(), new AprobarPlanRequest(b.version()));
    }

    private String tipoActiva(Long varianteId) {
        List<String> tipos = jdbc.queryForList(
                "SELECT tipo FROM alerta WHERE variante_id = ? AND estado = 'ACTIVA'", String.class, varianteId);
        assertThat(tipos).as("como máximo una alerta ACTIVA por variante").hasSizeLessThanOrEqualTo(1);
        return tipos.isEmpty() ? null : tipos.get(0);
    }

    @Test
    void aprobar_conPosicionSobreElUmbral_cierraStockBajoYMantieneAgotado() {
        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
        assertThat(tipoActiva(agotado)).isEqualTo("AGOTADO");

        // cuando: se aprueba un plan que lleva la posición de "bajo" a 3 + 10 = 13 > 5
        planAprobado(10);

        // entonces: STOCK_BAJO queda ATENDIDA automática; AGOTADO sigue (el pedido en camino no se puede vender)
        assertThat(tipoActiva(bajo)).isNull();
        assertThat(jdbc.queryForObject("SELECT atendida_por FROM alerta WHERE variante_id = ? AND tipo = 'STOCK_BAJO'",
                Long.class, bajo)).isNull();
        assertThat(jdbc.queryForObject("SELECT estado FROM alerta WHERE variante_id = ? AND tipo = 'STOCK_BAJO'",
                String.class, bajo)).isEqualTo("ATENDIDA");
        assertThat(tipoActiva(agotado)).isEqualTo("AGOTADO");
        assertThat(alertas.tipoEsperado(bajo)).isNull();
        assertThat(alertas.tipoEsperado(agotado)).isEqualTo(co.livorno.controlstock.inventario.modelo.TipoAlerta.AGOTADO);
    }

    @Test
    void aprobar_cantidadQueNoSuperaElUmbral_mantieneStockBajo() {
        // posición = 3 + 2 = 5 ≤ 5: sigue en STOCK_BAJO
        planAprobado(2);

        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
    }

    @Test
    void cancelar_planAprobado_reabreStockBajo() {
        PlanCompraDetalleResponse aprobado = planAprobado(10);
        assertThat(tipoActiva(bajo)).isNull();

        planes.cancelar(aprobado.id(), new CancelarPlanRequest("Proveedor sin stock"));

        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
        assertThat(tipoActiva(agotado)).isEqualTo("AGOTADO");
    }

    @Test
    void recibir_parcialmente_reevaluaConLoQueQuedaEnCamino() {
        // plan de 10 para "bajo" (posición 13). Se reciben 1: el físico pasa a 4 y no queda nada en camino (recepción
        // única): posición 4 ≤ 5, vuelve STOCK_BAJO.
        PlanCompraDetalleResponse aprobado = planAprobado(10);
        assertThat(tipoActiva(bajo)).isNull();
        List<LineaRecepcionRequest> lineas = new ArrayList<>();
        for (LineaPlanResponse l : aprobado.lineas()) {
            lineas.add(new LineaRecepcionRequest(l.id(), l.varianteId().equals(bajo) ? 1 : 5, new BigDecimal("20000.00")));
        }

        planes.recibir(aprobado.id(), new RecibirPlanRequest("Llegó incompleto", lineas));

        assertThat(planes.obtener(aprobado.id()).lineas()).extracting(LineaPlanResponse::enCaminoActual)
                .containsOnly(0L);
        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
        // "agotado" recibió 5 unidades: disponible 5 ≤ 5 y nada en camino → STOCK_BAJO (ya no AGOTADO)
        assertThat(tipoActiva(agotado)).isEqualTo("STOCK_BAJO");
    }

    @Test
    void ejecutar_conPedidoEnCamino_guardaMetricaContrafactualIgualAlConteoSql() {
        planAprobado(10);

        EjecucionAnalitica e = ejecucion.ejecutar(OrigenEjecucion.NOCTURNA, null);

        // el contrafactual es el conteo independiente de variantes activas con disponible ≤ max(0, umbral) sin
        // SIN_ROTACION activa; "bajo" (rescatada por el pedido) cuenta ahí pero no en la métrica real
        Integer esperado = jdbc.queryForObject("""
                SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id
                WHERE v.activo AND p.activo
                  AND (v.stock_fisico - v.stock_reservado) <= GREATEST(0, COALESCE(v.punto_reorden, v.stock_minimo))
                  AND v.id NOT IN (SELECT variante_id FROM alerta WHERE estado = 'ACTIVA' AND tipo = 'SIN_ROTACION')
                """, Integer.class);
        assertThat(e.getEnAlertaSinTransito()).isEqualTo(esperado);
        assertThat(e.getEnAlertaSinTransito() - e.getEnAlertaStock()).isEqualTo(1);
        Integer conPedido = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT d.variante_id) FROM detalle_plan d
                JOIN plan_compra pc ON pc.id = d.plan_id
                JOIN variante v ON v.id = d.variante_id JOIN producto p ON p.id = v.producto_id
                WHERE pc.estado = 'APROBADO' AND v.activo AND p.activo
                """, Integer.class);
        assertThat(e.getConPedidoEnCamino()).isEqualTo(conPedido).isGreaterThanOrEqualTo(2);
        // la re-evaluación masiva (en lote) dejó la alerta de "bajo" cerrada
        assertThat(tipoActiva(bajo)).isNull();
    }

    @Test
    void inventarioYReorden_exponenEnCamino() {
        planAprobado(10);
        ejecucion.ejecutar(OrigenEjecucion.NOCTURNA, null);
        String sku = "AL" + sufijo + "-S";

        List<InventarioItemResponse> filas = inventario.listar(sku, null, null, false, PageRequest.of(0, 10))
                .getContent();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).enCamino()).isEqualTo(10L);

        List<ReordenVarianteResponse> reorden = consultaAnalitica
                .listarReorden(null, null, sku, null, null, PageRequest.of(0, 10)).getContent();
        assertThat(reorden).hasSize(1);
        assertThat(reorden.get(0).enCamino()).isEqualTo(10L);
        assertThat(reorden.get(0).posicion()).isEqualTo(13L);
    }

    @Test
    void inventario_sinPedido_enCaminoCero() {
        List<InventarioItemResponse> filas = inventario
                .listar("AL" + sufijo + "-S", null, null, false, PageRequest.of(0, 10)).getContent();

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).enCamino()).isZero();
    }

    @Test
    void aprobar_dosPlanesDeLaMismaVariante_sumanEnCaminoYLaPosicion() {
        // dado: dos planes APROBADO de 2 unidades para "bajo": con uno la posicion es 5 (<= 5), con los dos 7
        PlanCompraDetalleResponse a = planes.crear(new CrearPlanRequest("A", List.of(new LineaPlanRequest(bajo, 2, null))));
        planes.aprobar(a.id(), new AprobarPlanRequest(a.version()));
        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
        PlanCompraDetalleResponse b = planes.crear(new CrearPlanRequest("B", List.of(new LineaPlanRequest(bajo, 2, null))));

        // cuando
        planes.aprobar(b.id(), new AprobarPlanRequest(b.version()));

        // entonces
        assertThat(tipoActiva(bajo)).isNull();
        String sku = "AL" + sufijo + "-S";
        assertThat(inventario.listar(sku, null, null, false, PageRequest.of(0, 10)).getContent().get(0).enCamino())
                .isEqualTo(4L);
    }

    @Test
    void recibir_conCantidadCeroEnUnaLinea_dejaDeContarEnCaminoYReabreStockBajo() {
        PlanCompraDetalleResponse aprobado = planAprobado(10);
        assertThat(tipoActiva(bajo)).isNull();
        List<LineaRecepcionRequest> lineas = new ArrayList<>();
        for (LineaPlanResponse l : aprobado.lineas()) {
            lineas.add(new LineaRecepcionRequest(l.id(), l.varianteId().equals(bajo) ? 0 : 5, new BigDecimal("20000.00")));
        }

        planes.recibir(aprobado.id(), new RecibirPlanRequest("Falto la talla S", lineas));

        // lo no recibido deja de estar en camino: posicion 3 <= 5; el fisico de "bajo" no cambio
        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, bajo)).isEqualTo(3);
        assertThat(planes.obtener(aprobado.id()).lineas()).extracting(LineaPlanResponse::enCaminoActual)
                .containsOnly(0L);
    }

    @Test
    void aprobar_siElListenerDePosicionFalla_laAprobacionQuedaConfirmadaYNoSePropaga() {
        // dado: la evaluacion de alertas lanza (el listener AFTER_COMMIT debe tragarse el error)
        org.mockito.Mockito.doThrow(new IllegalStateException("fallo simulado")).when(alertas)
                .evaluar(org.mockito.ArgumentMatchers.anyLong());
        PlanCompraDetalleResponse borrador = planes.crear(new CrearPlanRequest("Pedido",
                List.of(new LineaPlanRequest(bajo, 10, null))));

        // cuando: no lanza
        PlanCompraDetalleResponse aprobado = planes.aprobar(borrador.id(), new AprobarPlanRequest(borrador.version()));

        // entonces: el plan esta APROBADO en la base y la alerta quedo como estaba (sin re-evaluar)
        assertThat(jdbc.queryForObject("SELECT estado FROM plan_compra WHERE id = ?", String.class, aprobado.id()))
                .isEqualTo("APROBADO");
        assertThat(tipoActiva(bajo)).isEqualTo("STOCK_BAJO");
    }

    @Test
    void evaluar_varianteInactivaConPedidoEnCamino_noGeneraAlerta() {
        planAprobado(10);
        // dado: se desactiva "agotado" despues de aprobar (tenia AGOTADO y pedido en camino)
        jdbc.update("UPDATE variante SET activo = FALSE WHERE id = ?", agotado);

        alertas.evaluar(agotado);

        assertThat(tipoActiva(agotado)).isNull();
        assertThat(alertas.tipoEsperado(agotado)).isNull();
    }
}
