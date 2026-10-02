package co.livorno.controlstock.operacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Operación de punta a punta por HTTP (entradas, ajustes, kardex, ventas, devoluciones, anulación) y roles (T2, fase 2). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OperacionIntegracionTest {

    private static final String CLAVE = "Clave123!";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimitadorIntentosLogin limitador;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final List<Long> productosCreados = new ArrayList<>();
    private String sufijo;

    @BeforeEach
    void preparar() {
        limitador.limpiar();
        sufijo = Long.toString(System.nanoTime(), 36);
    }

    @AfterEach
    void limpiar() {
        for (Long productoId : productosCreados) {
            String variantes = "(SELECT id FROM variante WHERE producto_id = " + productoId + ")";
            String detalles = "(SELECT id FROM detalle_venta WHERE variante_id IN " + variantes + ")";
            List<Long> ventas = jdbc.queryForList("SELECT venta_id FROM detalle_venta WHERE variante_id IN "
                    + variantes, Long.class);
            jdbc.update("DELETE FROM devolucion WHERE detalle_venta_id IN " + detalles);
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            for (Long ventaId : ventas) {
                jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", ventaId);
                jdbc.update("DELETE FROM venta WHERE id = ?", ventaId);
            }
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", productoId);
            jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        }
        for (Long id : usuariosCreados) {
            usuarios.deleteById(id);
        }
    }

    // ---------- utilidades ----------

    private String tokenDe(Rol rol) throws Exception {
        String email = rol.name().toLowerCase() + "." + sufijo + "@livorno.test";
        usuariosCreados.add(usuarios.save(new Usuario("Prueba " + rol, email, encoder.encode(CLAVE), rol)).getId());
        return login(email, CLAVE);
    }

    private String tokenAdmin() throws Exception {
        return login("admin.prueba@livorno.test", "ClaveAdminPrueba123!");
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private ResultActions enviar(String metodo, String ruta, String token, String json) throws Exception {
        var req = "POST".equals(metodo) ? post(ruta) : get(ruta);
        req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req);
    }

    private static long id(String cuerpo, String ruta) {
        return ((Number) JsonPath.read(cuerpo, ruta)).longValue();
    }

    /** ADMIN crea producto (compra 25000, venta 59900) con una variante M/NEGRO; devuelve {productoId, varianteId}. */
    private long[] productoConVariante(String admin) throws Exception {
        long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String json = "{\"codigo\":\"op-" + sufijo + "\",\"nombre\":\"Camiseta Operacion\",\"categoriaId\":" + categoria
                + ",\"precioCompra\":25000.00,\"precioVenta\":59900.00}";
        long producto = id(enviar("POST", "/api/productos", admin, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        productosCreados.add(producto);
        String generado = enviar("POST", "/api/productos/" + producto + "/variantes/generar", admin,
                "{\"tallas\":[\"M\"],\"colores\":[\"Negro\"],\"stockMinimo\":2}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new long[] {producto, id(generado, "$.creadas[0].id")};
    }

    private void entrada(String token, long variante, int cantidad, String costo) throws Exception {
        enviar("POST", "/api/inventario/entradas", token, "{\"lineas\":[{\"varianteId\":" + variante
                + ",\"cantidad\":" + cantidad + ",\"costoUnitario\":" + costo + "}]}")
                .andExpect(status().isCreated());
    }

    private String venta(String token, long variante, int cantidad, String precio) throws Exception {
        String linea = "{\"varianteId\":" + variante + ",\"cantidad\":" + cantidad
                + (precio == null ? "" : ",\"precioUnitario\":" + precio) + "}";
        return enviar("POST", "/api/ventas", token, "{\"lineas\":[" + linea + "]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String devolucionJson(long detalle, int cantidad, boolean apta) {
        return "{\"detalleVentaId\":" + detalle + ",\"cantidad\":" + cantidad
                + ",\"motivo\":\"motivo de prueba\",\"aptaParaVenta\":" + apta + "}";
    }

    private int fisico(long variante) {
        return jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, variante);
    }

    private BigDecimal precioCompra(long producto) {
        return jdbc.queryForObject("SELECT precio_compra FROM producto WHERE id = ?", BigDecimal.class, producto);
    }

    // ---------- flujo ----------

    @Test
    void operacion_flujoCompleto_stockKardexPreciosYCostosCoherentes() throws Exception {
        // dado: ADMIN crea producto + variante; roles listos
        String admin = tokenAdmin();
        String inventario = tokenDe(Rol.INVENTARIO);
        String vendedor = tokenDe(Rol.VENDEDOR);
        String contabilidad = tokenDe(Rol.CONTABILIDAD);
        long[] ids = productoConVariante(admin);
        long producto = ids[0];
        long variante = ids[1];

        // entrada con costo 30000 cambia precioCompra; con costo 0 no
        entrada(inventario, variante, 10, "30000.00");
        assertThat(precioCompra(producto)).isEqualByComparingTo("30000.00");
        entrada(inventario, variante, 1, "0.00");
        assertThat(precioCompra(producto)).isEqualByComparingTo("30000.00");
        assertThat(fisico(variante)).isEqualTo(11);

        // ajuste negativo con observación
        enviar("POST", "/api/inventario/ajustes", inventario, "{\"varianteId\":" + variante
                + ",\"tipo\":\"AJUSTE_NEGATIVO\",\"cantidad\":2,\"observacion\":\"merma por humedad\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE_NEGATIVO"))
                .andExpect(jsonPath("$.fisicoAntes").value(11))
                .andExpect(jsonPath("$.fisicoDespues").value(9))
                .andExpect(jsonPath("$.costoUnitario").doesNotExist());

        // venta con descuento por VENDEDOR: costo oculto
        String ventaJson = venta(vendedor, variante, 4, "50000.00");
        long ventaId = id(ventaJson, "$.id");
        long detalle = id(ventaJson, "$.lineas[0].detalleId");
        enviar("GET", "/api/ventas/" + ventaId, vendedor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.total").value(200000.0))
                .andExpect(jsonPath("$.descuentoTotal").value(39600.0))
                .andExpect(jsonPath("$.lineas[0].precioLista").value(59900.0))
                .andExpect(jsonPath("$.lineas[0].precioUnitario").value(50000.0))
                .andExpect(jsonPath("$.lineas[0].subtotal").value(200000.0))
                .andExpect(jsonPath("$.lineas[0].costoUnitario").doesNotExist());
        for (String token : List.of(contabilidad, admin)) {
            enviar("GET", "/api/ventas/" + ventaId, token, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineas[0].costoUnitario").value(30000.0));
        }
        assertThat(fisico(variante)).isEqualTo(5);

        // devolución apta (+físico) y no apta (sin stock)
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", vendedor, devolucionJson(detalle, 1, true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aptaParaVenta").value(true));
        assertThat(fisico(variante)).isEqualTo(6);
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", vendedor, devolucionJson(detalle, 1, false))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aptaParaVenta").value(false));
        assertThat(fisico(variante)).isEqualTo(6);
        enviar("GET", "/api/ventas/" + ventaId, vendedor, null)
                .andExpect(jsonPath("$.lineas[0].cantidadDevuelta").value(2));

        // anular con devoluciones: 422 y nada cambia
        enviar("POST", "/api/ventas/" + ventaId + "/anular", admin, "{\"motivo\":\"error de captura\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(fisico(variante)).isEqualTo(6);

        // otra venta (precio de lista) y anulación por ADMIN: repone stock
        String venta2 = venta(vendedor, variante, 2, null);
        assertThat(fisico(variante)).isEqualTo(4);
        long venta2Id = id(venta2, "$.id");
        enviar("POST", "/api/ventas/" + venta2Id + "/anular", admin, "{\"motivo\":\"cliente se arrepintio\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.anuladaPor").value(notNullValue()));
        assertThat(fisico(variante)).isEqualTo(6);
        enviar("POST", "/api/ventas/" + venta2Id + "/anular", admin, "{\"motivo\":\"segunda vez\"}")
                .andExpect(status().isConflict());

        // kardex: cadena antes/después y tipo ANULACION_VENTA
        String kardex = enviar("GET", "/api/movimientos?varianteId=" + variante + "&size=100", inventario, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> tipos = JsonPath.read(kardex, "$.content[*].tipo");
        assertThat(tipos).containsExactly("ANULACION_VENTA", "VENTA", "DEVOLUCION", "VENTA", "AJUSTE_NEGATIVO",
                "ENTRADA", "ENTRADA");
        List<Integer> antes = JsonPath.read(kardex, "$.content[*].fisicoAntes");
        List<Integer> despues = JsonPath.read(kardex, "$.content[*].fisicoDespues");
        for (int i = 0; i < antes.size() - 1; i++) { // lista del más nuevo al más viejo
            assertThat(antes.get(i)).as("fisicoAntes[%d] = fisicoDespues[%d]", i, i + 1)
                    .isEqualTo(despues.get(i + 1));
        }
        assertThat(despues.get(0)).isEqualTo(6);
        assertThat(antes.get(antes.size() - 1)).isZero();
        enviar("GET", "/api/movimientos?varianteId=" + variante + "&tipo=ANULACION_VENTA", contabilidad, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].referenciaTipo").value("VENTA"))
                .andExpect(jsonPath("$.content[0].referenciaId").value((int) venta2Id))
                .andExpect(jsonPath("$.content[0].cantidad").value(2));
        enviar("GET", "/api/movimientos?varianteId=" + variante + "&tipo=ENTRADA", inventario, null)
                .andExpect(jsonPath("$.content[0].costoUnitario").value(0.0))
                .andExpect(jsonPath("$.content[1].costoUnitario").value(30000.0));
    }

    @Test
    void registrarVenta_conCantidadMayorAlDisponibleOPrecioSobreLista_responde422SinCambiarStock() throws Exception {
        String admin = tokenAdmin();
        long variante = productoConVariante(admin)[1];
        entrada(admin, variante, 3, "20000.00");

        enviar("POST", "/api/ventas", admin, "{\"lineas\":[{\"varianteId\":" + variante + ",\"cantidad\":4}]}")
                .andExpect(status().isUnprocessableContent());
        enviar("POST", "/api/ventas", admin, "{\"lineas\":[{\"varianteId\":" + variante
                + ",\"cantidad\":1,\"precioUnitario\":70000.00}]}").andExpect(status().isUnprocessableContent());

        assertThat(fisico(variante)).isEqualTo(3);
    }

    // ---------- matriz de roles ----------

    @Test
    void matrizRoles_vendedorInventarioYContabilidad_respetanLaMatrizDePermisos() throws Exception {
        String admin = tokenAdmin();
        String inventario = tokenDe(Rol.INVENTARIO);
        String vendedor = tokenDe(Rol.VENDEDOR);
        String contabilidad = tokenDe(Rol.CONTABILIDAD);
        long variante = productoConVariante(admin)[1];
        entrada(admin, variante, 10, "20000.00");
        String ventaJson = venta(admin, variante, 2, null);
        long ventaId = id(ventaJson, "$.id");
        long detalle = id(ventaJson, "$.lineas[0].detalleId");

        String entradaJson = "{\"lineas\":[{\"varianteId\":" + variante + ",\"cantidad\":1,\"costoUnitario\":1000.00}]}";
        String ajusteJson = "{\"varianteId\":" + variante
                + ",\"tipo\":\"AJUSTE_POSITIVO\",\"cantidad\":1,\"observacion\":\"conteo fisico\"}";
        String ventaNueva = "{\"lineas\":[{\"varianteId\":" + variante + ",\"cantidad\":1}]}";
        String anular = "{\"motivo\":\"motivo valido\"}";
        String devolver = devolucionJson(detalle, 1, true);

        // VENDEDOR: 403 en entradas, ajustes, movimientos y anular; sí lee ventas e inventario
        enviar("POST", "/api/inventario/entradas", vendedor, entradaJson).andExpect(status().isForbidden());
        enviar("POST", "/api/inventario/ajustes", vendedor, ajusteJson).andExpect(status().isForbidden());
        enviar("GET", "/api/movimientos", vendedor, null).andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/" + ventaId + "/anular", vendedor, anular).andExpect(status().isForbidden());
        enviar("GET", "/api/ventas", vendedor, null).andExpect(status().isOk());
        enviar("GET", "/api/inventario", vendedor, null).andExpect(status().isOk());

        // INVENTARIO: 403 en ventas (crear, listar, devolver, anular); sí entradas, ajustes y kardex
        enviar("POST", "/api/ventas", inventario, ventaNueva).andExpect(status().isForbidden());
        enviar("GET", "/api/ventas", inventario, null).andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", inventario, devolver)
                .andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/" + ventaId + "/anular", inventario, anular).andExpect(status().isForbidden());
        enviar("POST", "/api/inventario/entradas", inventario, entradaJson).andExpect(status().isCreated());
        enviar("POST", "/api/inventario/ajustes", inventario, ajusteJson).andExpect(status().isCreated());
        enviar("GET", "/api/movimientos", inventario, null).andExpect(status().isOk());

        // CONTABILIDAD: solo lectura
        enviar("GET", "/api/ventas", contabilidad, null).andExpect(status().isOk());
        enviar("GET", "/api/ventas/" + ventaId, contabilidad, null).andExpect(status().isOk());
        enviar("GET", "/api/movimientos", contabilidad, null).andExpect(status().isOk());
        enviar("POST", "/api/ventas", contabilidad, ventaNueva).andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", contabilidad, devolver)
                .andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/" + ventaId + "/anular", contabilidad, anular).andExpect(status().isForbidden());
        enviar("POST", "/api/inventario/entradas", contabilidad, entradaJson).andExpect(status().isForbidden());
        enviar("POST", "/api/inventario/ajustes", contabilidad, ajusteJson).andExpect(status().isForbidden());

        // sin token: 401
        mvc.perform(get("/api/ventas")).andExpect(status().isUnauthorized());
    }

    // ---------- concurrencia ----------

    @Test
    void devolver_dosNoAptasSimultaneasQueSuperanLoVendido_unaRecibe422YLoDevueltoNoExcedeLoVendido()
            throws Exception {
        // dado: venta de 5 y dos devoluciones no aptas de 3 (juntas 6 > 5)
        String admin = tokenAdmin();
        String vendedor = tokenDe(Rol.VENDEDOR);
        long variante = productoConVariante(admin)[1];
        entrada(admin, variante, 5, "20000.00");
        String ventaJson = venta(vendedor, variante, 5, null);
        long ventaId = id(ventaJson, "$.id");
        long detalle = id(ventaJson, "$.lineas[0].detalleId");

        // cuando: se disparan a la vez
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", vendedor,
                        devolucionJson(detalle, 3, false)).andReturn().getResponse().getStatus();
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            estados.add(f.get());
        }
        pool.shutdown();

        // entonces: una 201 y la otra 422 (o 409); lo devuelto nunca supera lo vendido
        assertThat(estados).filteredOn(s -> s == 201).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 422 || s == 409).hasSize(1);
        Integer devuelto = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM devolucion"
                + " WHERE detalle_venta_id = ?", Integer.class, detalle);
        assertThat(devuelto).isEqualTo(3).isLessThanOrEqualTo(5);
        assertThat(fisico(variante)).isZero();
    }

    @Test
    void anularYDevolver_enParaleloLaMismaVenta_unaSolaGanaYNuncaQuedaAnuladaConDevoluciones() throws Exception {
        // dado: venta de 2 unidades; ADMIN anula y VENDEDOR devuelve una (apta) a la vez
        String admin = tokenAdmin();
        String vendedor = tokenDe(Rol.VENDEDOR);
        long variante = productoConVariante(admin)[1];
        entrada(admin, variante, 2, "20000.00");
        String ventaJson = venta(vendedor, variante, 2, null);
        long ventaId = id(ventaJson, "$.id");
        long detalle = id(ventaJson, "$.lineas[0].detalleId");

        // cuando: se disparan a la vez
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> anulacion = pool.submit(() -> {
            salida.await();
            return enviar("POST", "/api/ventas/" + ventaId + "/anular", admin, "{\"motivo\":\"error de captura\"}")
                    .andReturn().getResponse().getStatus();
        });
        Future<Integer> devolucion = pool.submit(() -> {
            salida.await();
            return enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", vendedor,
                    devolucionJson(detalle, 1, true)).andReturn().getResponse().getStatus();
        });
        salida.countDown();
        int estadoAnular = anulacion.get();
        int estadoDevolver = devolucion.get();
        pool.shutdown();

        // entonces: exactamente una operación tiene éxito (anular 200 o devolver 201), la otra 422/409
        boolean anuloGano = estadoAnular == 200;
        boolean devolvioGano = estadoDevolver == 201;
        assertThat(anuloGano ^ devolvioGano).as("anular=%d, devolver=%d", estadoAnular, estadoDevolver).isTrue();
        assertThat(anuloGano ? estadoDevolver : estadoAnular).isIn(422, 409);

        String estado = jdbc.queryForObject("SELECT estado FROM venta WHERE id = ?", String.class, ventaId);
        Integer devoluciones = jdbc.queryForObject("SELECT COUNT(*) FROM devolucion WHERE detalle_venta_id = ?",
                Integer.class, detalle);
        assertThat("ANULADA".equals(estado) && devoluciones > 0).as("venta ANULADA con devoluciones").isFalse();
        assertThat(estado).isEqualTo(anuloGano ? "ANULADA" : "REGISTRADA");
        assertThat(devoluciones).isEqualTo(anuloGano ? 0 : 1);
        assertThat(fisico(variante)).isEqualTo(anuloGano ? 2 : 1); // anulada repone 2; devuelta 1 queda 0+1
    }

    @Test
    void validacionTextos_observacionOMotivoCortoTrasRecortar_responde400() throws Exception {
        String admin = tokenAdmin();
        long variante = productoConVariante(admin)[1];
        entrada(admin, variante, 3, "20000.00");
        long ventaId = id(venta(admin, variante, 1, null), "$.id");
        long detalle = jdbc.queryForObject("SELECT id FROM detalle_venta WHERE venta_id = ?", Long.class, ventaId);

        enviar("POST", "/api/inventario/ajustes", admin, "{\"varianteId\":" + variante
                + ",\"tipo\":\"AJUSTE_POSITIVO\",\"cantidad\":1,\"observacion\":\"  ab   \"}")
                .andExpect(status().isBadRequest());
        enviar("POST", "/api/ventas/" + ventaId + "/anular", admin, "{\"motivo\":\"  abc  \"}")
                .andExpect(status().isBadRequest());
        enviar("POST", "/api/ventas/" + ventaId + "/devoluciones", admin, "{\"detalleVentaId\":" + detalle
                + ",\"cantidad\":1,\"motivo\":\"  abcd \",\"aptaParaVenta\":true}")
                .andExpect(status().isBadRequest());
        // justo en el límite (5 caracteres recortados) sí pasa
        enviar("POST", "/api/ventas/" + ventaId + "/anular", admin, "{\"motivo\":\"  abcde  \"}")
                .andExpect(status().isOk());
    }
}
