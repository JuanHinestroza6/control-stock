package co.livorno.controlstock.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T1, fase 6: el flujo de compra de punta a punta (ADR-010) y la matriz de roles completa. Todo por HTTP con el JWT de
 * cada rol y datos creados por la API (no la semilla), con un reloj que viaja al pasado para fabricar la historia.
 *
 * <p>Producto P (compra 10.000, venta 50.000, lead time 10 días, creado hace 160 días) con tres variantes que venden
 * 10 unidades cada 10 días (hace 150 ... 10 días):
 * <ul>
 *   <li>vM: entrada 155, quedan 5 (STOCK_BAJO tras el recálculo).</li>
 *   <li>vL: entrada 150, quedan 0 (AGOTADO).</li>
 *   <li>vXL: entrada 155, quedan 5 (STOCK_BAJO; su plan se cancela al final).</li>
 * </ul>
 * Los datos se aíslan con un sufijo único en el código y una categoría propia.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, CompraFlujoIntegracionTest.RelojDePrueba.class})
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CompraFlujoIntegracionTest {

    private static final String CLAVE = "Clave123!";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String PLANES = "/api/compras/planes";

    /** Reloj que puede fijarse en el pasado y volver al sistema. */
    static class RelojMutable extends Clock {
        private volatile Instant fijo;

        void fijarHaceDias(int dias) {
            fijo = Instant.now().minusSeconds(dias * 86400L);
        }

        void volverAlPresente() {
            fijo = null;
        }

        @Override
        public ZoneId getZone() {
            return BOGOTA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant f = fijo;
            return f != null ? f : Instant.now();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimitadorIntentosLogin limitador;
    @Autowired RelojMutable reloj;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final Map<String, String> tokens = new HashMap<>();
    private long ejecucionBase;
    private Long categoriaId;
    private String sufijo;
    private long producto;
    private long vM;
    private long vL;
    private long vXL;

    // ---------- preparación y limpieza ----------

    @BeforeEach
    void preparar() throws Exception {
        limitador.limpiar();
        String base = Long.toString(System.nanoTime(), 36).toUpperCase();
        sufijo = base.substring(Math.max(0, base.length() - 8));
        ejecucionBase = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ejecucion_analitica", Long.class);
        for (Rol rol : List.of(Rol.INVENTARIO, Rol.VENDEDOR, Rol.CONTABILIDAD)) {
            String email = rol.name().toLowerCase() + "." + sufijo + "@livorno.test";
            usuariosCreados.add(usuarios.save(new Usuario("Prueba " + rol, email, encoder.encode(CLAVE), rol)).getId());
        }
        for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            token(rol); // con el reloj real: un token emitido en el pasado nacería vencido
        }
        categoriaId = id(ok(enviar("POST", "/api/categorias", token("ADMIN"), "{\"nombre\":\"Compra " + sufijo + "\"}")
                .andExpect(status().isCreated())), "$.id");
        armarEscenario();
    }

    @AfterEach
    void limpiar() {
        reloj.volverAlPresente();
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        if (producto != 0) {
            String variantes = "(SELECT id FROM variante WHERE producto_id = " + producto + ")";
            List<Long> planes = jdbc.queryForList("SELECT DISTINCT plan_id FROM detalle_plan WHERE variante_id IN "
                    + variantes, Long.class);
            List<Long> ventas = jdbc.queryForList("SELECT venta_id FROM detalle_venta WHERE variante_id IN "
                    + variantes, Long.class);
            for (Long planId : planes) {
                jdbc.update("DELETE FROM detalle_plan WHERE plan_id = ?", planId);
                jdbc.update("DELETE FROM plan_compra WHERE id = ?", planId);
            }
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM alerta WHERE variante_id IN " + variantes);
            for (Long ventaId : ventas) {
                jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", ventaId);
                jdbc.update("DELETE FROM venta WHERE id = ?", ventaId);
            }
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", producto);
            jdbc.update("DELETE FROM producto WHERE id = ?", producto);
        }
        jdbc.update("DELETE FROM analitica_variante WHERE ejecucion_id > ?", ejecucionBase);
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id > ?", ejecucionBase);
        if (categoriaId != null) {
            jdbc.update("DELETE FROM categoria WHERE id = ?", categoriaId);
        }
        for (Long id : usuariosCreados) {
            usuarios.deleteById(id);
        }
    }

    // ---------- utilidades HTTP ----------

    private String token(String rol) throws Exception {
        String guardado = tokens.get(rol);
        if (guardado != null) {
            return guardado;
        }
        limitador.limpiar();
        String email = rol.equals("ADMIN") ? "admin.prueba@livorno.test"
                : rol.toLowerCase() + "." + sufijo + "@livorno.test";
        String clave = rol.equals("ADMIN") ? "ClaveAdminPrueba123!" : CLAVE;
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String t = JsonPath.read(cuerpo, "$.token");
        tokens.put(rol, t);
        return t;
    }

    private ResultActions enviar(String metodo, String ruta, String token, String json) throws Exception {
        MockHttpServletRequestBuilder req = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            default -> get(ruta);
        };
        if (token != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req);
    }

    private static long id(String cuerpo, String ruta) {
        return ((Number) JsonPath.read(cuerpo, ruta)).longValue();
    }

    private String ok(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    // ---------- armado del escenario por la API ----------

    private long variante(String talla) {
        return jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ? AND talla = ?", Long.class, producto,
                talla);
    }

    private void armarEscenario() throws Exception {
        reloj.fijarHaceDias(160);
        String json = "{\"codigo\":\"CF" + sufijo + "\",\"nombre\":\"Camiseta Compra " + sufijo + "\",\"categoriaId\":"
                + categoriaId + ",\"precioCompra\":10000.00,\"precioVenta\":50000.00,\"leadTimeDias\":10}";
        producto = id(ok(enviar("POST", "/api/productos", token("ADMIN"), json).andExpect(status().isCreated())),
                "$.id");
        enviar("POST", "/api/productos/" + producto + "/variantes/generar", token("ADMIN"),
                "{\"tallas\":[\"M\",\"L\",\"XL\"],\"colores\":[\"Negro\"],\"stockMinimo\":2}")
                .andExpect(status().isCreated());
        vM = variante("M");
        vL = variante("L");
        vXL = variante("XL");
        enviar("POST", "/api/inventario/entradas", token("INVENTARIO"), "{\"lineas\":["
                + "{\"varianteId\":" + vM + ",\"cantidad\":155,\"costoUnitario\":10000.00},"
                + "{\"varianteId\":" + vL + ",\"cantidad\":150,\"costoUnitario\":10000.00},"
                + "{\"varianteId\":" + vXL + ",\"cantidad\":155,\"costoUnitario\":10000.00}]}")
                .andExpect(status().isCreated());
        for (int dias = 150; dias >= 10; dias -= 10) {
            reloj.fijarHaceDias(dias);
            enviar("POST", "/api/ventas", token("VENDEDOR"), "{\"lineas\":["
                    + "{\"varianteId\":" + vM + ",\"cantidad\":10},"
                    + "{\"varianteId\":" + vL + ",\"cantidad\":10},"
                    + "{\"varianteId\":" + vXL + ",\"cantidad\":10}]}").andExpect(status().isCreated());
        }
        reloj.volverAlPresente();
    }

    private String recalcular() throws Exception {
        return ok(enviar("POST", "/api/analitica/recalcular", token("ADMIN"), null).andExpect(status().isOk()));
    }

    private List<String> activas(long varianteId) {
        return jdbc.queryForList("SELECT tipo FROM alerta WHERE variante_id = ? AND estado = 'ACTIVA' ORDER BY tipo",
                String.class, varianteId);
    }

    private int stock(long varianteId) {
        return jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, varianteId);
    }

    /** Lee un número; si la ruta es un filtro devuelve una lista y se toma el primer elemento. */
    private static int entero(String cuerpo, String ruta) {
        Object v = JsonPath.read(cuerpo, ruta);
        if (v instanceof List<?> l) {
            v = l.get(0);
        }
        return ((Number) v).intValue();
    }

    private static String texto(String cuerpo, String ruta) {
        Object v = JsonPath.read(cuerpo, ruta);
        if (v instanceof List<?> l) {
            v = l.get(0);
        }
        return (String) v;
    }

    /** Métricas de la ejecución contra conteos SQL escritos aparte (no reutilizan el código de producción). */
    private void verificarMetricas(String ejecucion) {
        assertThat((String) JsonPath.read(ejecucion, "$.estado")).isEqualTo("EXITOSA");
        int activasSql = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1", Integer.class);
        int enAlertaSql = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1 AND EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id"
                + " AND a.estado = 'ACTIVA' AND a.tipo IN ('AGOTADO','STOCK_BAJO'))", Integer.class);
        // contrafactual: sin contar lo que viene en camino (disponible <= umbral), sin las que tienen SIN_ROTACION
        int sinTransitoSql = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1 AND (v.stock_fisico - v.stock_reservado) <="
                + " CASE WHEN v.stock_fisico - v.stock_reservado = 0 THEN 0"
                + " ELSE COALESCE(v.punto_reorden, v.stock_minimo) END"
                + " AND NOT EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id AND a.estado = 'ACTIVA'"
                + " AND a.tipo = 'SIN_ROTACION')", Integer.class);
        int conPedidoSql = jdbc.queryForObject("SELECT COUNT(DISTINCT d.variante_id) FROM detalle_plan d"
                + " JOIN plan_compra pc ON pc.id = d.plan_id JOIN variante v ON v.id = d.variante_id"
                + " JOIN producto p ON p.id = v.producto_id WHERE pc.estado = 'APROBADO' AND v.activo = 1"
                + " AND p.activo = 1 AND d.cantidad_aprobada > 0", Integer.class);
        assertThat(entero(ejecucion, "$.variantesActivas")).as("variantesActivas").isEqualTo(activasSql);
        assertThat(entero(ejecucion, "$.enAlertaStock")).as("enAlertaStock").isEqualTo(enAlertaSql);
        assertThat(entero(ejecucion, "$.enAlertaSinTransito")).as("enAlertaSinTransito").isEqualTo(sinTransitoSql);
        assertThat(entero(ejecucion, "$.conPedidoEnCamino")).as("conPedidoEnCamino").isEqualTo(conPedidoSql);
        // la ejecución guardada en la base coincide con lo que respondió la API
        Map<String, Object> guardada = jdbc.queryForMap("SELECT en_alerta_stock, en_alerta_sin_transito FROM"
                + " ejecucion_analitica WHERE id = ?", id(ejecucion, "$.id"));
        assertThat(((Number) guardada.get("en_alerta_stock")).intValue()).isEqualTo(enAlertaSql);
        assertThat(((Number) guardada.get("en_alerta_sin_transito")).intValue()).isEqualTo(sinTransitoSql);
    }

    // ---------- flujo completo ----------

    @Test
    void flujoDeCompra_recalcularSugerirEditarAprobarRecibirYCancelar_cuadraAlertasKardexYMetricas() throws Exception {
        // 1. recalcular (ADMIN): pronóstico guardado, ROP y alertas por posición
        String r1 = recalcular();
        assertThat(entero(r1, "$.productosPronosticados")).isGreaterThanOrEqualTo(1);
        assertThat(entero(r1, "$.conHoltWinters") + entero(r1, "$.conSes"))
                .isEqualTo(entero(r1, "$.productosPronosticados"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE producto_id = ? AND variante_id IS NULL",
                Integer.class, producto)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pronostico WHERE producto_id = ? AND variante_id IS NOT"
                + " NULL", Integer.class, producto)).isEqualTo(9);
        enviar("GET", "/api/analitica/pronosticos/" + producto, token("INVENTARIO"), null).andExpect(status().isOk());
        assertThat(activas(vM)).containsExactly("STOCK_BAJO");
        assertThat(activas(vL)).containsExactly("AGOTADO");
        assertThat(activas(vXL)).containsExactly("STOCK_BAJO");
        verificarMetricas(r1);
        assertThat(entero(r1, "$.enAlertaSinTransito")).isEqualTo(entero(r1, "$.enAlertaStock")); // nada en camino

        // 2. sugerencia (INVENTARIO) de la categoría propia: tres líneas, con justificación
        String sugerido = ok(enviar("POST", PLANES + "/sugerido", token("INVENTARIO"),
                "{\"categoriaId\":" + categoriaId + ",\"observacion\":\"Sugerido " + sufijo + "\"}")
                .andExpect(status().isCreated()));
        long planId = id(sugerido, "$.id");
        assertThat((String) JsonPath.read(sugerido, "$.estado")).isEqualTo("BORRADOR");
        assertThat((String) JsonPath.read(sugerido, "$.origen")).isEqualTo("SUGERIDO");
        List<Map<String, Object>> lineas = JsonPath.read(sugerido, "$.lineas");
        assertThat(lineas).hasSize(3);
        for (Map<String, Object> l : lineas) {
            assertThat((String) l.get("justificacion")).isNotBlank();
            assertThat((Integer) l.get("cantidadSugerida")).isPositive().isEqualTo(l.get("cantidadAprobada"));
        }
        // las variantes ya están en un BORRADOR: una segunda sugerencia no tiene candidatas
        enviar("POST", PLANES + "/sugerido", token("ADMIN"), "{\"categoriaId\":" + categoriaId + "}")
                .andExpect(status().isUnprocessableContent());

        // 3. editar el borrador (INVENTARIO): quita vXL y fija 40 en vM; con la versión vieja responde 409
        long versionInicial = id(sugerido, "$.version");
        int cantidadL = entero(sugerido, "$.lineas[?(@.varianteId==" + vL + ")].cantidadAprobada");
        String edicion = "{\"version\":" + versionInicial + ",\"observacion\":\"Editado " + sufijo + "\",\"lineas\":["
                + "{\"varianteId\":" + vM + ",\"cantidad\":40,\"costoEstimado\":11000.00},"
                + "{\"varianteId\":" + vL + ",\"cantidad\":" + cantidadL + ",\"costoEstimado\":11000.00}]}";
        String editado = ok(enviar("PUT", PLANES + "/" + planId, token("INVENTARIO"), edicion)
                .andExpect(status().isOk()));
        assertThat((List<?>) JsonPath.read(editado, "$.lineas")).hasSize(2);
        assertThat(id(editado, "$.version")).isGreaterThan(versionInicial);
        assertThat(entero(editado, "$.lineas[?(@.varianteId==" + vM + ")].cantidadAprobada")).isEqualTo(40);
        assertThat(texto(editado, "$.lineas[?(@.varianteId==" + vM + ")].justificacion")).isNotBlank();
        assertThat(entero(editado, "$.lineas[?(@.varianteId==" + vM + ")].cantidadSugerida")).isPositive();
        enviar("PUT", PLANES + "/" + planId, token("INVENTARIO"), edicion).andExpect(status().isConflict());
        long versionEditada = id(editado, "$.version");

        // 4. aprobar: INVENTARIO 403; ADMIN 200. STOCK_BAJO de vM se cierra sola; AGOTADO de vL sigue abierta
        enviar("POST", PLANES + "/" + planId + "/aprobar", token("INVENTARIO"), "{\"version\":" + versionEditada + "}")
                .andExpect(status().isForbidden());
        String aprobado = ok(enviar("POST", PLANES + "/" + planId + "/aprobar", token("ADMIN"),
                "{\"version\":" + versionEditada + "}").andExpect(status().isOk()));
        assertThat((String) JsonPath.read(aprobado, "$.estado")).isEqualTo("APROBADO");
        assertThat(entero(aprobado, "$.lineas[?(@.varianteId==" + vM + ")].enCaminoActual")).isEqualTo(40);
        assertThat(activas(vM)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE variante_id = ? AND tipo = 'STOCK_BAJO' AND"
                + " estado = 'ATENDIDA' AND atendida_por IS NULL", Integer.class, vM)).isEqualTo(1);
        assertThat(activas(vL)).containsExactly("AGOTADO");

        // un segundo plan APROBADO para vXL (manual): su alerta también se cierra
        String borrador2 = ok(enviar("POST", PLANES, token("INVENTARIO"), "{\"observacion\":\"Segundo " + sufijo
                + "\",\"lineas\":[{\"varianteId\":" + vXL + ",\"cantidad\":30}]}").andExpect(status().isCreated()));
        long plan2 = id(borrador2, "$.id");
        enviar("POST", PLANES + "/" + plan2 + "/aprobar", token("ADMIN"), "{\"version\":" + id(borrador2, "$.version")
                + "}").andExpect(status().isOk());
        assertThat(activas(vXL)).isEmpty();

        // métrica con tránsito: dos alertas menos que sin tránsito y tres variantes con pedido
        String r2 = recalcular();
        verificarMetricas(r2);
        assertThat(entero(r2, "$.conPedidoEnCamino")).isEqualTo(3);
        assertThat(entero(r2, "$.enAlertaSinTransito")).isEqualTo(entero(r2, "$.enAlertaStock") + 2);
        assertThat(activas(vM)).isEmpty();
        assertThat(activas(vL)).containsExactly("AGOTADO");
        assertThat(activas(vXL)).isEmpty();

        // 5. recibir plan 1 (INVENTARIO): ENTRADAS con referencia PLAN_COMPRA/{id}, precioCompra, stock = kardex
        List<Number> detalles = JsonPath.read(aprobado, "$.lineas[*].id");
        List<Number> aprobadas = JsonPath.read(aprobado, "$.lineas[*].cantidadAprobada");
        List<Number> variantesPlan = JsonPath.read(aprobado, "$.lineas[*].varianteId");
        StringBuilder recepcion = new StringBuilder("{\"lineas\":[");
        for (int i = 0; i < detalles.size(); i++) {
            recepcion.append(i == 0 ? "" : ",").append("{\"detalleId\":").append(detalles.get(i))
                    .append(",\"cantidadRecibida\":").append(aprobadas.get(i)).append(",\"costoUnitario\":12000}");
        }
        recepcion.append("]}");
        String recibido = ok(enviar("POST", PLANES + "/" + planId + "/recibir", token("INVENTARIO"),
                recepcion.toString()).andExpect(status().isOk()));
        assertThat((String) JsonPath.read(recibido, "$.estado")).isEqualTo("RECIBIDO");
        List<Number> enCaminoLineas = JsonPath.read(recibido, "$.lineas[*].enCaminoActual");
        for (Number enCamino : enCaminoLineas) {
            assertThat(enCamino.longValue()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE tipo = 'ENTRADA' AND referencia_tipo ="
                + " 'PLAN_COMPRA' AND referencia_id = ?", Integer.class, planId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT precio_compra FROM producto WHERE id = ?", BigDecimal.class, producto))
                .isEqualByComparingTo("12000.00");
        for (int i = 0; i < variantesPlan.size(); i++) {
            long v = variantesPlan.get(i).longValue();
            int sumaKardex = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN tipo IN ('ENTRADA','DEVOLUCION',"
                    + "'AJUSTE_POSITIVO','ANULACION_VENTA') THEN cantidad WHEN tipo IN ('VENTA','VENTA_RESERVA',"
                    + "'AJUSTE_NEGATIVO') THEN -cantidad ELSE 0 END), 0) FROM movimiento WHERE variante_id = ?",
                    Integer.class, v);
            assertThat(stock(v)).as("stock de %d", v).isEqualTo(sumaKardex);
            int base = v == vM ? 5 : 0;
            assertThat(stock(v)).isEqualTo(base + aprobadas.get(i).intValue());
        }
        assertThat(activas(vL)).doesNotContain("AGOTADO"); // llegó mercancía
        // el plan 2 sigue en camino (vXL), el recibido ya no
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM detalle_plan d JOIN plan_compra pc ON pc.id = d.plan_id"
                + " WHERE pc.estado = 'APROBADO' AND d.variante_id IN (?, ?)", Integer.class, vM, vL)).isZero();

        // 6. cancelar el otro APROBADO: INVENTARIO 403; ADMIN 200 y la STOCK_BAJO de vXL se reabre
        String cancelar = "{\"motivo\":\"Proveedor sin existencias\"}";
        enviar("POST", PLANES + "/" + plan2 + "/cancelar", token("INVENTARIO"), cancelar)
                .andExpect(status().isForbidden());
        assertThat(activas(vXL)).isEmpty();
        String cancelado = ok(enviar("POST", PLANES + "/" + plan2 + "/cancelar", token("ADMIN"), cancelar)
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(cancelado, "$.estado")).isEqualTo("CANCELADO");
        assertThat(activas(vXL)).containsExactly("STOCK_BAJO");

        // 7. recalcular al final: las métricas siguen cuadrando con SQL y ya no hay nada en camino de este escenario
        String r3 = recalcular();
        verificarMetricas(r3);
        assertThat(entero(r3, "$.enAlertaSinTransito")).isGreaterThanOrEqualTo(entero(r3, "$.enAlertaStock"));
        assertThat(activas(vXL)).containsExactly("STOCK_BAJO");
    }

    // ---------- matriz de roles ----------

    /** Endpoint de la fase 6 con el código de éxito y los roles que permite la matriz de dominio.md. */
    private enum Ep {
        LISTAR_PLANES(200, "ADMIN", "INVENTARIO", "CONTABILIDAD"),
        VER_PLAN(200, "ADMIN", "INVENTARIO", "CONTABILIDAD"),
        CREAR_PLAN(201, "ADMIN", "INVENTARIO"),
        SUGERIR_PLAN(201, "ADMIN", "INVENTARIO"),
        EDITAR_BORRADOR(200, "ADMIN", "INVENTARIO"),
        APROBAR_PLAN(200, "ADMIN"),
        CANCELAR_BORRADOR(200, "ADMIN", "INVENTARIO"),
        CANCELAR_APROBADO(200, "ADMIN"),
        RECIBIR_PLAN(200, "ADMIN", "INVENTARIO"),
        TENDENCIAS(200, "ADMIN", "INVENTARIO", "CONTABILIDAD"),
        LISTAR_PRONOSTICOS(200, "ADMIN", "INVENTARIO", "CONTABILIDAD"),
        VER_PRONOSTICO(200, "ADMIN", "INVENTARIO", "CONTABILIDAD");

        final int exito;
        final List<String> permitidos;

        Ep(int exito, String... permitidos) {
            this.exito = exito;
            this.permitidos = List.of(permitidos);
        }
    }

    private String nuevoBorrador() throws Exception {
        return ok(enviar("POST", PLANES, token("ADMIN"), "{\"observacion\":\"Matriz " + sufijo
                + "\",\"lineas\":[{\"varianteId\":" + vXL + ",\"cantidad\":5}]}").andExpect(status().isCreated()));
    }

    private String nuevoAprobado() throws Exception {
        String b = nuevoBorrador();
        return ok(enviar("POST", PLANES + "/" + id(b, "$.id") + "/aprobar", token("ADMIN"),
                "{\"version\":" + id(b, "$.version") + "}").andExpect(status().isOk()));
    }

    private void descartarBorradores() {
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM (SELECT pc.id FROM plan_compra pc"
                + " JOIN detalle_plan d ON d.plan_id = pc.id WHERE pc.estado = 'BORRADOR' AND d.variante_id IN"
                + " (SELECT id FROM variante WHERE producto_id = " + producto + ")) x)");
        jdbc.update("DELETE FROM plan_compra WHERE estado = 'BORRADOR' AND id NOT IN (SELECT plan_id FROM detalle_plan)"
                + " AND observacion LIKE '%" + sufijo + "%'");
    }

    private int llamar(Ep ep, String rol) throws Exception {
        String t = rol.equals("SIN_TOKEN") ? null : token(rol);
        ResultActions r;
        switch (ep) {
            case LISTAR_PLANES -> r = enviar("GET", PLANES, t, null);
            case VER_PLAN -> {
                String b = nuevoBorrador();
                r = enviar("GET", PLANES + "/" + id(b, "$.id"), t, null);
            }
            case CREAR_PLAN -> r = enviar("POST", PLANES, t, "{\"observacion\":\"Matriz " + sufijo
                    + "\",\"lineas\":[{\"varianteId\":" + vXL + ",\"cantidad\":5}]}");
            case SUGERIR_PLAN -> {
                descartarBorradores();
                r = enviar("POST", PLANES + "/sugerido", t, "{\"categoriaId\":" + categoriaId + ",\"observacion\":\""
                        + "Matriz " + sufijo + "\"}");
            }
            case EDITAR_BORRADOR -> {
                String b = nuevoBorrador();
                r = enviar("PUT", PLANES + "/" + id(b, "$.id"), t, "{\"version\":" + id(b, "$.version")
                        + ",\"lineas\":[{\"varianteId\":" + vXL + ",\"cantidad\":6}]}");
            }
            case APROBAR_PLAN -> {
                String b = nuevoBorrador();
                r = enviar("POST", PLANES + "/" + id(b, "$.id") + "/aprobar", t, "{\"version\":" + id(b, "$.version")
                        + "}");
            }
            case CANCELAR_BORRADOR -> {
                String b = nuevoBorrador();
                r = enviar("POST", PLANES + "/" + id(b, "$.id") + "/cancelar", t, "{\"motivo\":\"Ya no se necesita\"}");
            }
            case CANCELAR_APROBADO -> {
                String a = nuevoAprobado();
                r = enviar("POST", PLANES + "/" + id(a, "$.id") + "/cancelar", t, "{\"motivo\":\"Ya no se necesita\"}");
            }
            case RECIBIR_PLAN -> {
                String a = nuevoAprobado();
                r = enviar("POST", PLANES + "/" + id(a, "$.id") + "/recibir", t, "{\"lineas\":[{\"detalleId\":"
                        + id(a, "$.lineas[0].id") + ",\"cantidadRecibida\":5,\"costoUnitario\":10000}]}");
            }
            case TENDENCIAS -> r = enviar("GET", "/api/analitica/tendencias", t, null);
            case LISTAR_PRONOSTICOS -> r = enviar("GET", "/api/analitica/pronosticos?categoriaId=" + categoriaId, t,
                    null);
            case VER_PRONOSTICO -> r = enviar("GET", "/api/analitica/pronosticos/" + producto, t, null);
            default -> throw new IllegalStateException();
        }
        return r.andReturn().getResponse().getStatus();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD", "SIN_TOKEN"})
    void matrizDeRoles_cadaEndpointDeComprasTendenciasYPronosticos_respetaElDominio(String rol) throws Exception {
        // dado: pronóstico calculado (el detalle del pronóstico y la sugerencia lo necesitan)
        recalcular();
        for (Ep ep : Ep.values()) {
            // cuando
            int estado = llamar(ep, rol);
            // entonces: 401 sin token, 403 sin permiso, el código de éxito del endpoint con permiso
            int esperado = rol.equals("SIN_TOKEN") ? 401 : ep.permitidos.contains(rol) ? ep.exito : 403;
            assertThat(estado).as("%s como %s", ep, rol).isEqualTo(esperado);
        }
    }

    @Test
    void matrizDeRoles_inventarioCancelaBorradorPeroNoAprobadoYLosRechazadosNoCambianNada() throws Exception {
        // dado un borrador y un aprobado
        String borrador = nuevoBorrador();
        String aprobado = nuevoAprobado();
        long idBorrador = id(borrador, "$.id");
        long idAprobado = id(aprobado, "$.id");
        int movimientosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?",
                Integer.class, vXL);

        // cuando INVENTARIO intenta cancelar el aprobado y VENDEDOR/CONTABILIDAD tocan el plan
        enviar("POST", PLANES + "/" + idAprobado + "/cancelar", token("INVENTARIO"), "{\"motivo\":\"Cambio de planes\"}")
                .andExpect(status().isForbidden());
        for (String rol : List.of("VENDEDOR", "CONTABILIDAD")) {
            enviar("POST", PLANES + "/" + idBorrador + "/aprobar", token(rol), "{\"version\":" + id(borrador,
                    "$.version") + "}").andExpect(status().isForbidden());
            enviar("POST", PLANES + "/" + idAprobado + "/recibir", token(rol), "{\"lineas\":[{\"detalleId\":"
                    + id(aprobado, "$.lineas[0].id") + ",\"cantidadRecibida\":5,\"costoUnitario\":10000}]}")
                    .andExpect(status().isForbidden());
        }

        // entonces: nada cambió de estado ni tocó el kardex
        assertThat(jdbc.queryForObject("SELECT estado FROM plan_compra WHERE id = ?", String.class, idAprobado))
                .isEqualTo("APROBADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM plan_compra WHERE id = ?", String.class, idBorrador))
                .isEqualTo("BORRADOR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?", Integer.class, vXL))
                .isEqualTo(movimientosAntes);

        // y sí puede descartar el borrador
        enviar("POST", PLANES + "/" + idBorrador + "/cancelar", token("INVENTARIO"), "{\"motivo\":\"Ya no se necesita\"}")
                .andExpect(status().isOk());
    }
}
