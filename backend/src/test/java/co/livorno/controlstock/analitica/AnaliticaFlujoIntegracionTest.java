package co.livorno.controlstock.analitica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T1, fase 5: la analítica de punta a punta. Todo el escenario se arma por HTTP real (JWT de cada rol) con un reloj
 * controlable que viaja al pasado: catálogo, entradas, ventas (una anulada), devoluciones (apta y no apta) y una reserva
 * convertida ocurren entre hace 100 y hace 4 días; luego el reloj vuelve al presente y el ADMIN recalcula.
 *
 * <p>Escenario (producto A: compra 10.000, venta 50.000; producto B: sin ventas, creado hace 100 días):
 * <ul>
 *   <li>vA1 (M): V1 hace 60 d x10; V2 hace 50 d x5 (anulada); V3 hace 40 d x3; devolución apta x1 hace 39 d.
 *       Neto: 12 unidades, 600.000.</li>
 *   <li>vA2 (L): V3 x4 a 45.000; devolución no apta x1; reserva x2 convertida hace 4 d (V4, a 50.000). Neto: 5 unidades,
 *       235.000.</li>
 *   <li>vA3 (XL): creada hace 10 días, sin stock: AGOTADO y SIN_HISTORIA (umbral = stock mínimo 3).</li>
 *   <li>vB1 (M) y vB2 (L): 10 unidades c/u, nunca vendidas: SIN_DEMANDA, ROP 0 y SIN_ROTACION.</li>
 * </ul>
 * Los datos se aíslan con un sufijo único en los códigos y una categoría propia.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestcontainersConfiguration.class, AnaliticaFlujoIntegracionTest.RelojDePrueba.class})
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AnaliticaFlujoIntegracionTest {

    private static final String CLAVE = "Clave123!";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    /** Reloj que puede fijarse en el pasado y volver al sistema. Reemplaza al de la aplicación (solo en esta prueba). */
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
    @Autowired AlertaService alertas;
    @Autowired RelojMutable reloj;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final List<Long> productos = new ArrayList<>();
    private final Map<String, String> tokens = new HashMap<>();
    private long ejecucionBase;
    private Long categoriaId;
    private String sufijo;
    private long vA1;
    private long vA2;
    private long vA3;
    private long vB1;
    private long vB2;
    private long productoA;
    private long productoB;

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
        categoriaId = id(enviar("POST", "/api/categorias", token("ADMIN"), "{\"nombre\":\"Flujo " + sufijo + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        armarEscenario();
    }

    @AfterEach
    void limpiar() {
        reloj.volverAlPresente();
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        String variantes = "(SELECT id FROM variante WHERE producto_id IN (" + csv(productos) + "))";
        if (!productos.isEmpty()) {
            String detalles = "(SELECT id FROM detalle_venta WHERE variante_id IN " + variantes + ")";
            List<Long> ventas = jdbc.queryForList("SELECT venta_id FROM detalle_venta WHERE variante_id IN "
                    + variantes, Long.class);
            jdbc.update("DELETE FROM devolucion WHERE detalle_venta_id IN " + detalles);
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM alerta WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM reserva WHERE variante_id IN " + variantes);
            for (Long ventaId : ventas) {
                jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", ventaId);
                jdbc.update("DELETE FROM venta WHERE id = ?", ventaId);
            }
            jdbc.update("DELETE FROM variante WHERE producto_id IN (" + csv(productos) + ")");
            jdbc.update("DELETE FROM producto WHERE id IN (" + csv(productos) + ")");
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

    private static String csv(List<Long> ids) {
        return ids.isEmpty() ? "NULL" : ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("NULL");
    }

    // ---------- utilidades HTTP ----------

    /**
     * Token del rol, pedido una sola vez con el reloj real: el JWT se valida contra la hora del sistema (no contra el
     * {@code Clock} de la aplicación), así que un token emitido en el pasado nacería vencido.
     */
    private String token(String rol) throws Exception {
        String guardado = tokens.get(rol);
        if (guardado != null) {
            return guardado;
        }
        limitador.limpiar();
        String email;
        String clave;
        if (rol.equals("ADMIN")) {
            email = "admin.prueba@livorno.test";
            clave = "ClaveAdminPrueba123!";
        } else {
            email = rol.toLowerCase() + "." + sufijo + "@livorno.test";
            clave = CLAVE;
        }
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String t = JsonPath.read(cuerpo, "$.token");
        tokens.put(rol, t);
        return t;
    }

    private void irAlPasado(int dias) {
        reloj.fijarHaceDias(dias);
    }

    private void irAlPresente() {
        reloj.volverAlPresente();
    }

    private ResultActions enviar(String metodo, String ruta, String token, String json) throws Exception {
        var req = switch (metodo) {
            case "POST" -> post(ruta);
            case "DELETE" -> delete(ruta);
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

    private static long detalleDe(String venta, long varianteId) {
        List<Number> ids = JsonPath.read(venta, "$.lineas[?(@.varianteId==" + varianteId + ")].detalleId");
        return ids.get(0).longValue();
    }

    private static long id(String cuerpo, String ruta) {
        return ((Number) JsonPath.read(cuerpo, ruta)).longValue();
    }

    private String ok(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    // ---------- armado del escenario por la API ----------

    private long crearProducto(String letra, String nombre) throws Exception {
        String json = "{\"codigo\":\"" + letra + "F" + sufijo + "\",\"nombre\":\"" + nombre + " " + sufijo
                + "\",\"categoriaId\":" + categoriaId + ",\"precioCompra\":10000.00,\"precioVenta\":50000.00"
                + ",\"leadTimeDias\":10}";
        long producto = id(ok(enviar("POST", "/api/productos", token("ADMIN"), json).andExpect(status().isCreated())),
                "$.id");
        productos.add(producto);
        return producto;
    }

    private void generar(long producto, String tallas, int stockMinimo) throws Exception {
        enviar("POST", "/api/productos/" + producto + "/variantes/generar", token("ADMIN"),
                "{\"tallas\":" + tallas + ",\"colores\":[\"Negro\"],\"stockMinimo\":" + stockMinimo + "}")
                .andExpect(status().isCreated());
    }

    private long variante(long producto, String talla) {
        return jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ? AND talla = ?", Long.class,
                producto, talla);
    }

    private void entrada(long variante, int cantidad, String costo) throws Exception {
        enviar("POST", "/api/inventario/entradas", token("INVENTARIO"), "{\"lineas\":[{\"varianteId\":" + variante
                + ",\"cantidad\":" + cantidad + ",\"costoUnitario\":" + costo + "}]}").andExpect(status().isCreated());
    }

    private static String linea(long variante, int cantidad, String precio) {
        return "{\"varianteId\":" + variante + ",\"cantidad\":" + cantidad
                + (precio == null ? "" : ",\"precioUnitario\":" + precio) + "}";
    }

    private String vender(String rol, String... lineas) throws Exception {
        return ok(enviar("POST", "/api/ventas", token(rol), "{\"lineas\":[" + String.join(",", lineas) + "]}")
                .andExpect(status().isCreated()));
    }

    private void devolver(long ventaId, long detalleId, boolean apta) throws Exception {
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", token("VENDEDOR"), "{\"detalleVentaId\":" + detalleId
                + ",\"cantidad\":1,\"motivo\":\"motivo de prueba\",\"aptaParaVenta\":" + apta + "}")
                .andExpect(status().isCreated());
    }

    private void armarEscenario() throws Exception {
        for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            token(rol);
        }
        // hace 100 días: catálogo y entradas
        irAlPasado(100);
        productoA = crearProducto("A", "Camiseta A");
        generar(productoA, "[\"M\",\"L\"]", 2);
        productoB = crearProducto("B", "Camiseta B");
        generar(productoB, "[\"M\",\"L\"]", 2);
        vA1 = variante(productoA, "M");
        vA2 = variante(productoA, "L");
        vB1 = variante(productoB, "M");
        vB2 = variante(productoB, "L");
        entrada(vA1, 100, "10000.00");
        entrada(vA2, 100, "10000.00");
        entrada(vB1, 10, "8000.00");
        entrada(vB2, 10, "8000.00");

        // hace 60 días: V1
        irAlPasado(60);
        vender("VENDEDOR", linea(vA1, 10, null));

        // hace 50 días: V2, anulada al día siguiente por el ADMIN
        irAlPasado(50);
        long v2 = id(vender("VENDEDOR", linea(vA1, 5, null)), "$.id");
        irAlPasado(49);
        enviar("POST", "/api/ventas/" + v2 + "/anular", token("ADMIN"), "{\"motivo\":\"error de captura\"}")
                .andExpect(status().isOk());

        // hace 40 días: V3 con descuento en vA2; hace 39 días, devolución apta (vA1) y no apta (vA2)
        irAlPasado(40);
        String v3 = vender("VENDEDOR", linea(vA1, 3, null), linea(vA2, 4, "45000.00"));
        long v3Id = id(v3, "$.id");
        long detalleA1 = detalleDe(v3, vA1);
        long detalleA2 = detalleDe(v3, vA2);
        irAlPasado(39);
        devolver(v3Id, detalleA1, true);
        devolver(v3Id, detalleA2, false);

        // hace 10 días: aparece la talla XL (sin stock, con poca historia)
        irAlPasado(10);
        generar(productoA, "[\"XL\"]", 3);
        vA3 = variante(productoA, "XL");

        // hace 5 días se aparta y hace 4 se convierte en venta
        irAlPasado(5);
        String reservas = ok(enviar("POST", "/api/reservas", token("INVENTARIO"),
                "{\"clienteNombre\":\"Cliente Prueba\",\"clienteContacto\":\"3000000000\",\"vigenciaHoras\":120"
                        + ",\"lineas\":[{\"varianteId\":" + vA2 + ",\"cantidad\":2}]}").andExpect(status().isCreated()));
        long reservaId = id(reservas, "$[0].id");
        irAlPasado(4);
        enviar("POST", "/api/ventas/desde-reservas", token("VENDEDOR"), "{\"reservaIds\":[" + reservaId + "]}")
                .andExpect(status().isCreated());

        // presente: una unidad apartada y activa en vA1 (el recálculo no debe tocarla)
        irAlPresente();
        enviar("POST", "/api/reservas", token("INVENTARIO"),
                "{\"clienteNombre\":\"Cliente Hoy\",\"clienteContacto\":\"3111111111\""
                        + ",\"lineas\":[{\"varianteId\":" + vA1 + ",\"cantidad\":1}]}").andExpect(status().isCreated());
    }

    private String recalcular() throws Exception {
        return ok(enviar("POST", "/api/analitica/recalcular", token("ADMIN"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXITOSA")));
    }

    private List<Map<String, Object>> filas(String cuerpo, long varianteId) {
        return JsonPath.read(cuerpo, "$.content[?(@.varianteId==" + varianteId + ")]");
    }

    private Map<String, Object> fila(String cuerpo, long varianteId) {
        List<Map<String, Object>> l = filas(cuerpo, varianteId);
        assertThat(l).as("fila de la variante %d", varianteId).hasSize(1);
        return l.get(0);
    }

    private List<String> activas(long varianteId) {
        return jdbc.queryForList("SELECT tipo FROM alerta WHERE variante_id = ? AND estado = 'ACTIVA' ORDER BY tipo",
                String.class, varianteId);
    }

    // ---------- pruebas ----------

    @Test
    void recalcular_comoAdmin_terminaExitosaYLaMetricaGuardadaIgualAlConteoSql() throws Exception {
        // cuando
        String r = recalcular();

        // entonces: la última ejecución es la misma
        String ultima = ok(enviar("GET", "/api/analitica/ejecuciones/ultima", token("CONTABILIDAD"), null)
                .andExpect(status().isOk()));
        for (String campo : List.of("id", "estado", "origen", "variantesAnalizadas", "claseA", "claseB", "claseC",
                "conPuntoReorden", "variantesActivas", "enAlertaStock", "porcentajeAlertaStock", "sinRotacionActivas",
                "alertasEvaluadas", "alertasFallidas", "fechaCorte")) {
            assertThat((Object) JsonPath.read(ultima, "$." + campo)).as(campo)
                    .isEqualTo(JsonPath.read(r, "$." + campo));
        }
        assertThat((String) JsonPath.read(r, "$.origen")).isEqualTo("MANUAL");
        assertThat((String) JsonPath.read(r, "$.fechaCorte")).isEqualTo(LocalDate.now(BOGOTA).toString());
        assertThat((Integer) JsonPath.read(r, "$.alertasFallidas")).isZero();
        int clases = (Integer) JsonPath.read(r, "$.claseA") + (Integer) JsonPath.read(r, "$.claseB")
                + (Integer) JsonPath.read(r, "$.claseC");
        assertThat(clases).isEqualTo((Integer) JsonPath.read(r, "$.variantesAnalizadas"));

        // métrica = conteo SQL independiente (variantes activas de productos activos con AGOTADO o STOCK_BAJO)
        int activasSql = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1", Integer.class);
        int enAlertaSql = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1 AND EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id"
                + " AND a.estado = 'ACTIVA' AND a.tipo IN ('AGOTADO','STOCK_BAJO'))", Integer.class);
        int sinRotacionSql = jdbc.queryForObject("SELECT COUNT(DISTINCT v.id) FROM variante v JOIN producto p ON p.id"
                + " = v.producto_id JOIN alerta a ON a.variante_id = v.id WHERE v.activo = 1 AND p.activo = 1"
                + " AND a.estado = 'ACTIVA' AND a.tipo = 'SIN_ROTACION'", Integer.class);
        assertThat((Integer) JsonPath.read(ultima, "$.variantesActivas")).isEqualTo(activasSql);
        assertThat((Integer) JsonPath.read(ultima, "$.enAlertaStock")).isEqualTo(enAlertaSql);
        assertThat((Integer) JsonPath.read(ultima, "$.sinRotacionActivas")).isEqualTo(sinRotacionSql);
        assertThat(sinRotacionSql).isGreaterThanOrEqualTo(2);
        BigDecimal esperado = BigDecimal.valueOf(enAlertaSql * 100.0 / activasSql).setScale(1, RoundingMode.HALF_UP);
        assertThat(new BigDecimal(JsonPath.read(ultima, "$.porcentajeAlertaStock").toString()))
                .isEqualByComparingTo(esperado);
        // lo guardado en la tabla (el porcentaje se deriva de estos dos conteos)
        Map<String, Object> guardada = jdbc.queryForMap("SELECT variantes_activas, en_alerta_stock FROM"
                + " ejecucion_analitica WHERE id = ?", id(r, "$.id"));
        assertThat(((Number) guardada.get("variantes_activas")).intValue()).isEqualTo(activasSql);
        assertThat(((Number) guardada.get("en_alerta_stock")).intValue()).isEqualTo(enAlertaSql);
    }

    @Test
    void abc_ingresosNetosExcluyenLaAnuladaYRestanDevolucionesYInventarioTraeLaClase() throws Exception {
        recalcular();

        // ABC: vA1 12 unidades / 600.000 (la anulada de 5 no cuenta, la devolución apta resta 1); vA2 5 / 235.000
        String abc = ok(enviar("GET", "/api/analitica/abc?q=" + sufijo + "&size=50", token("INVENTARIO"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(5)));
        Map<String, Object> a1 = fila(abc, vA1);
        Map<String, Object> a2 = fila(abc, vA2);
        assertThat(a1.get("unidadesNetas")).isEqualTo(12);
        assertThat(new BigDecimal(a1.get("ingresoNeto").toString())).isEqualByComparingTo("600000.00");
        assertThat(a2.get("unidadesNetas")).isEqualTo(5);
        assertThat(new BigDecimal(a2.get("ingresoNeto").toString())).isEqualByComparingTo("235000.00");
        for (long sinVentas : List.of(vA3, vB1, vB2)) {
            Map<String, Object> f = fila(abc, sinVentas);
            assertThat(f.get("unidadesNetas")).isEqualTo(0);
            assertThat(new BigDecimal(f.get("ingresoNeto").toString())).isEqualByComparingTo("0");
            assertThat(f.get("claseAbc")).isEqualTo("C");
        }
        // ordenado por ingreso: vA1 antes que vA2, y el acumulado no baja
        assertThat((Integer) a1.get("posicion")).isLessThan((Integer) a2.get("posicion"));
        assertThat(new BigDecimal(a1.get("acumulado").toString()))
                .isLessThanOrEqualTo(new BigDecimal(a2.get("acumulado").toString()));

        // resumen: ventana [corte - 365, corte), total >= lo de estos datos y clases cuadran con la ejecución
        LocalDate hoy = LocalDate.now(BOGOTA);
        String resumen = ok(enviar("GET", "/api/analitica/abc/resumen", token("CONTABILIDAD"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanaDesde").value(hoy.minusDays(365).toString()))
                .andExpect(jsonPath("$.ventanaHasta").value(hoy.minusDays(1).toString()))
                .andExpect(jsonPath("$.clases.length()").value(3)));
        assertThat(new BigDecimal(JsonPath.read(resumen, "$.totalIngreso").toString()))
                .isGreaterThanOrEqualTo(new BigDecimal("835000"));
        List<Integer> variantesPorClase = JsonPath.read(resumen, "$.clases[*].variantes");
        assertThat(variantesPorClase.stream().mapToInt(Integer::intValue).sum())
                .isEqualTo((Integer) JsonPath.read(resumen, "$.ejecucion.variantesAnalizadas"));

        // inventario: claseAbc y punto de reorden coinciden con la instantánea y con la base (todos los roles)
        String inventario = ok(enviar("GET", "/api/inventario?q=" + sufijo + "&size=50", token("VENDEDOR"), null)
                .andExpect(status().isOk()));
        for (long v : List.of(vA1, vA2, vA3, vB1, vB2)) {
            Map<String, Object> item = fila(inventario, v);
            String claseSql = jdbc.queryForObject("SELECT clase_abc FROM variante WHERE id = ?", String.class, v);
            assertThat(item.get("claseAbc")).as("clase de %d", v).isEqualTo(claseSql).isEqualTo(
                    fila(abc, v).get("claseAbc"));
        }
    }

    @Test
    void reorden_cadaVarianteTraeMetodoYUmbralEfectivoCoherentes() throws Exception {
        recalcular();

        String reorden = ok(enviar("GET", "/api/analitica/reorden?q=" + sufijo + "&size=50", token("CONTABILIDAD"),
                null).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(5)));

        // con ventas y más de 30 días de historia: Poisson (μL < 5) y ROP positivo que es el umbral efectivo
        for (long v : List.of(vA1, vA2)) {
            Map<String, Object> f = fila(reorden, v);
            Integer rop = (Integer) f.get("puntoReorden");
            assertThat(f.get("metodo")).isEqualTo("POISSON");
            assertThat((Integer) f.get("diasHistoria")).isGreaterThanOrEqualTo(30);
            assertThat(rop).isNotNull().isPositive();
            assertThat(f.get("umbralEfectivo")).isEqualTo(rop);
            assertThat(f.get("fuenteUmbral")).isEqualTo("ROP");
            assertThat(jdbc.queryForObject("SELECT punto_reorden FROM variante WHERE id = ?", Integer.class, v))
                    .isEqualTo(rop);
        }
        assertThat(fila(reorden, vA1).get("stockDisponible")).isEqualTo(87); // 88 físicas - 1 apartada
        assertThat(fila(reorden, vA2).get("stockDisponible")).isEqualTo(94);

        // sin ventas con historia: SIN_DEMANDA, ROP 0 (solo AGOTADO)
        for (long v : List.of(vB1, vB2)) {
            Map<String, Object> f = fila(reorden, v);
            assertThat(f.get("metodo")).isEqualTo("SIN_DEMANDA");
            assertThat(f.get("puntoReorden")).isEqualTo(0);
            assertThat(f.get("umbralEfectivo")).isEqualTo(0);
            assertThat(f.get("fuenteUmbral")).isEqualTo("ROP");
        }

        // poca historia: sin ROP, el umbral es el stock mínimo
        Map<String, Object> nueva = fila(reorden, vA3);
        assertThat(nueva.get("metodo")).isEqualTo("SIN_HISTORIA");
        assertThat((Integer) nueva.get("diasHistoria")).isLessThan(30);
        assertThat(nueva.get("puntoReorden")).isNull();
        assertThat(nueva.get("umbralEfectivo")).isEqualTo(3);
        assertThat(nueva.get("fuenteUmbral")).isEqualTo("STOCK_MINIMO");
        assertThat(jdbc.queryForObject("SELECT punto_reorden FROM variante WHERE id = ?", Integer.class, vA3)).isNull();
    }

    @Test
    void alertas_tipoEsperadoEsLaAlertaActivaYUnaVentaPosteriorCierraLaSinRotacionDelProducto() throws Exception {
        recalcular();

        // coherencia: cada variante tiene exactamente la alerta que dice tipoEsperado (o ninguna)
        for (long v : List.of(vA1, vA2, vA3, vB1, vB2)) {
            TipoAlerta esperado = alertas.tipoEsperado(v);
            List<String> activas = activas(v);
            if (esperado == null) {
                assertThat(activas).as("alertas de %d", v).isEmpty();
            } else {
                assertThat(activas).as("alertas de %d", v).containsExactly(esperado.name());
            }
        }
        assertThat(activas(vA3)).containsExactly("AGOTADO");
        assertThat(activas(vB1)).containsExactly("SIN_ROTACION");
        assertThat(activas(vB2)).containsExactly("SIN_ROTACION");
        assertThat(activas(vA1)).isEmpty(); // el producto A vendió hace 4 días
        assertThat(activas(vA2)).isEmpty();

        // la API de alertas (VENDEDOR solo lee) muestra la SIN_ROTACION
        enviar("GET", "/api/alertas?estado=ACTIVA&tipo=SIN_ROTACION&size=200", token("VENDEDOR"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.varianteId==" + vB1 + ")].tipo").value("SIN_ROTACION"));

        // cuando: se vende una unidad de vB1 hoy
        vender("VENDEDOR", linea(vB1, 1, null));

        // entonces: se cierran las SIN_ROTACION de TODAS las variantes del producto B, sin usuario (automática)
        assertThat(activas(vB1)).isEmpty();
        assertThat(activas(vB2)).isEmpty();
        assertThat(alertas.tipoEsperado(vB2)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alerta WHERE variante_id IN (?, ?) AND tipo ="
                + " 'SIN_ROTACION' AND estado = 'ATENDIDA' AND atendida_por IS NULL", Integer.class, vB1, vB2))
                .isEqualTo(2);
    }

    @Test
    void masVendidos_excluyeLaAnuladaYRestaLasDevoluciones() throws Exception {
        LocalDate hoy = LocalDate.now(BOGOTA);
        String rango = "desde=" + hoy.minusDays(200) + "&hasta=" + hoy + "&categoriaId=" + categoriaId;

        // por variante y unidades: vA1 12 (no 17 ni 11), vA2 5; la categoría es solo de estos datos
        String r = ok(enviar("GET", "/api/analitica/mas-vendidos?" + rango + "&agrupacion=VARIANTE&limite=50",
                token("VENDEDOR"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnidades").value(17))
                .andExpect(jsonPath("$.totalIngreso").value(835000.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].varianteId").value(vA1))
                .andExpect(jsonPath("$.items[0].unidades").value(12))
                .andExpect(jsonPath("$.items[0].ingreso").value(600000.0))
                .andExpect(jsonPath("$.items[1].varianteId").value(vA2))
                .andExpect(jsonPath("$.items[1].unidades").value(5))
                .andExpect(jsonPath("$.items[1].ingreso").value(235000.0)));
        assertThat(r).doesNotContain("costo");

        // por producto
        enviar("GET", "/api/analitica/mas-vendidos?" + rango + "&limite=5", token("CONTABILIDAD"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productoId").value(productoA))
                .andExpect(jsonPath("$.items[0].unidades").value(17))
                .andExpect(jsonPath("$.items[0].ingreso").value(835000.0));

        // por ingreso, otro rol
        enviar("GET", "/api/analitica/mas-vendidos?" + rango + "&criterio=INGRESO&agrupacion=VARIANTE",
                token("INVENTARIO"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].varianteId").value(vA1));
    }

    @Test
    void matrizDeRoles_respetaLaMatrizDelDominioYSinTokenResponde401() throws Exception {
        recalcular();
        int ejecucionesAntes = jdbc.queryForObject("SELECT COUNT(*) FROM ejecucion_analitica", Integer.class);
        String hoy = LocalDate.now(BOGOTA).toString();
        String masVendidos = "/api/analitica/mas-vendidos?categoriaId=" + categoriaId + "&desde=" + hoy.substring(0, 4)
                + "-01-01&hasta=" + hoy;
        List<String> lecturas = List.of("/api/analitica/abc", "/api/analitica/abc/resumen", "/api/analitica/reorden",
                "/api/analitica/ejecuciones/ultima");

        // VENDEDOR: solo más vendidos
        enviar("GET", masVendidos, token("VENDEDOR"), null).andExpect(status().isOk());
        for (String ruta : lecturas) {
            enviar("GET", ruta, token("VENDEDOR"), null).andExpect(status().isForbidden());
        }
        enviar("POST", "/api/analitica/recalcular", token("VENDEDOR"), null).andExpect(status().isForbidden());

        // INVENTARIO y CONTABILIDAD: leen todo, no recalculan
        for (String rol : List.of("INVENTARIO", "CONTABILIDAD")) {
            enviar("GET", masVendidos, token(rol), null).andExpect(status().isOk());
            for (String ruta : lecturas) {
                enviar("GET", ruta, token(rol), null).andExpect(status().isOk());
            }
            enviar("POST", "/api/analitica/recalcular", token(rol), null).andExpect(status().isForbidden());
        }

        // ADMIN: todo
        enviar("GET", masVendidos, token("ADMIN"), null).andExpect(status().isOk());
        for (String ruta : lecturas) {
            enviar("GET", ruta, token("ADMIN"), null).andExpect(status().isOk());
        }

        // los rechazados no dejaron ejecuciones
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ejecucion_analitica", Integer.class))
                .isEqualTo(ejecucionesAntes);

        // sin token: 401 en todo
        for (String ruta : lecturas) {
            enviar("GET", ruta, null, null).andExpect(status().isUnauthorized());
        }
        enviar("GET", masVendidos, null, null).andExpect(status().isUnauthorized());
        enviar("POST", "/api/analitica/recalcular", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void recalcular_noCambiaStockFisicoNiReservadoNiVersionDeNingunaVariante() throws Exception {
        // dado: foto de todas las variantes de la base antes del recálculo
        String sql = "SELECT id, stock_fisico, stock_reservado, version FROM variante ORDER BY id";
        List<Map<String, Object>> antes = jdbc.queryForList(sql);
        assertThat(antes).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class, vA1))
                .isEqualTo(1);

        // cuando
        recalcular();
        recalcular();

        // entonces: ni stock ni @Version cambiaron (solo punto_reorden y clase_abc)
        assertThat(jdbc.queryForList(sql)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT clase_abc FROM variante WHERE id = ?", String.class, vA1)).isNotNull();
        // y el kardex sigue cuadrando con el stock de las variantes del escenario
        for (long v : List.of(vA1, vA2, vA3, vB1, vB2)) {
            Integer fisico = jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, v);
            Integer ultimoKardex = jdbc.queryForList("SELECT fisico_despues FROM movimiento WHERE variante_id = ?"
                    + " ORDER BY id DESC LIMIT 1", Integer.class, v).stream().findFirst().orElse(0);
            assertThat(fisico).as("físico de %d", v).isEqualTo(ultimoKardex);
        }
    }
}
