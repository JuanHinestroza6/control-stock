package co.livorno.controlstock.operacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Reservas y alertas de punta a punta por HTTP (T2, fase 3): crear, extender, cancelar, convertir en venta, anular,
 * desactivación bloqueada, alertas por evento, atender, resumen, matriz de roles y contrato JSON del frontend.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ReservasAlertasIntegracionTest {

    private static final String CLAVE = "Clave123!";
    private static final String CLIENTE = "Maria Perez";
    private static final String CONTACTO = "3001234567";

    private static final Set<String> CLAVES_RESERVA = Set.of("id", "numero", "varianteId", "sku", "productoNombre",
            "talla", "color", "cantidad", "precioVenta", "clienteNombre", "clienteContacto", "estado", "creadaEn",
            "venceEn", "creadaPor", "cerradaEn", "cerradaPor", "motivoCancelacion", "ventaId", "ventaNumero");
    private static final Set<String> CLAVES_ALERTA = Set.of("id", "tipo", "estado", "varianteId", "sku",
            "productoNombre", "talla", "color", "stockDisponible", "umbral", "creadaEn", "revisadaEn", "atendidaEn",
            "atendidaPor");
    private static final Set<String> CLAVES_RESUMEN = Set.of("activas", "agotado", "stockBajo", "sinRotacion");
    private static final Set<String> CLAVES_VENTA = Set.of("id", "numero", "fecha", "vendedor", "estado", "total",
            "descuentoTotal", "anuladaEn", "anuladaPor", "motivoAnulacion", "lineas", "reservas");
    private static final Set<String> CLAVES_LINEA = Set.of("detalleId", "varianteId", "sku", "productoNombre", "talla",
            "color", "cantidad", "precioLista", "precioUnitario", "costoUnitario", "subtotal", "cantidadDevuelta",
            "devoluciones");
    private static final Set<String> CLAVES_PAGINA = Set.of("content", "page");
    private static final Set<String> CLAVES_META = Set.of("size", "number", "totalElements", "totalPages");

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
            List<Long> ventas = jdbc.queryForList("SELECT DISTINCT venta_id FROM detalle_venta WHERE variante_id IN "
                    + variantes, Long.class);
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM alerta WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM reserva WHERE variante_id IN " + variantes); // antes que venta (FK venta_id)
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
        var req = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "PATCH" -> patch(ruta);
            default -> get(ruta);
        };
        req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req);
    }

    private static long id(String cuerpo, String ruta) {
        return ((Number) JsonPath.read(cuerpo, ruta)).longValue();
    }

    /** Primer elemento numérico de una ruta que devuelve lista (filtros JsonPath). */
    private static long primero(String cuerpo, String ruta) {
        return ((Number) JsonPath.<List<Number>>read(cuerpo, ruta).get(0)).longValue();
    }

    private static String cuerpo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    /** ADMIN crea un producto (compra 25000, venta 59900) con variantes M y L NEGRO (stockMinimo 2): {producto, M, L}. */
    private long[] productoConDosVariantes(String admin) throws Exception {
        long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String json = "{\"codigo\":\"ra-" + sufijo + "\",\"nombre\":\"Camiseta Reservas\",\"categoriaId\":" + categoria
                + ",\"precioCompra\":25000.00,\"precioVenta\":59900.00}";
        long producto = id(cuerpo(enviar("POST", "/api/productos", admin, json).andExpect(status().isCreated())),
                "$.id");
        productosCreados.add(producto);
        String generado = cuerpo(enviar("POST", "/api/productos/" + producto + "/variantes/generar", admin,
                "{\"tallas\":[\"M\",\"L\"],\"colores\":[\"Negro\"],\"stockMinimo\":2}").andExpect(status().isCreated()));
        return new long[] {producto, primero(generado, "$.creadas[?(@.talla=='M')].id"),
                primero(generado, "$.creadas[?(@.talla=='L')].id")};
    }

    private void entrada(String token, long variante, int cantidad) throws Exception {
        enviar("POST", "/api/inventario/entradas", token, "{\"lineas\":[{\"varianteId\":" + variante
                + ",\"cantidad\":" + cantidad + ",\"costoUnitario\":20000.00}]}").andExpect(status().isCreated());
    }

    private static String linea(long variante, int cantidad) {
        return "{\"varianteId\":" + variante + ",\"cantidad\":" + cantidad + "}";
    }

    private String reservarJson(String cliente, String contacto, String... lineas) {
        return "{\"clienteNombre\":\"" + cliente + "\",\"clienteContacto\":\"" + contacto + "\",\"lineas\":["
                + String.join(",", lineas) + "]}";
    }

    private String reservar(String token, String cliente, String contacto, String... lineas) throws Exception {
        return cuerpo(enviar("POST", "/api/reservas", token, reservarJson(cliente, contacto, lineas))
                .andExpect(status().isCreated()));
    }

    private void ventaDirecta(String token, long variante, int cantidad) throws Exception {
        enviar("POST", "/api/ventas", token, "{\"lineas\":[" + linea(variante, cantidad) + "]}")
                .andExpect(status().isCreated());
    }

    private int fisico(long variante) {
        return jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, variante);
    }

    private int reservado(long variante) {
        return jdbc.queryForObject("SELECT stock_reservado FROM variante WHERE id = ?", Integer.class, variante);
    }

    private String estadoReserva(long reserva) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reserva);
    }

    /** Alertas de una variante con el estado dado, como JSON de la lista de AlertaResponse. */
    private String alertas(String token, long variante, String estado) throws Exception {
        String pagina = cuerpo(enviar("GET", "/api/alertas?estado=" + estado + "&size=100", token, null)
                .andExpect(status().isOk()));
        return JsonPath.parse(JsonPath.<List<Object>>read(pagina, "$.content[?(@.varianteId==" + variante + ")]"))
                .jsonString();
    }

    private List<String> tiposAlerta(String token, long variante, String estado) throws Exception {
        return JsonPath.read(alertas(token, variante, estado), "$[*].tipo");
    }

    private long idAlerta(String token, long variante, String estado, String tipo) throws Exception {
        return primero(alertas(token, variante, estado), "$[?(@.tipo=='" + tipo + "')].id");
    }

    private List<String> tiposKardex(String token, long variante) throws Exception {
        String kardex = cuerpo(enviar("GET", "/api/movimientos?varianteId=" + variante + "&size=100", token, null)
                .andExpect(status().isOk()));
        return JsonPath.read(kardex, "$.content[*].tipo");
    }

    private long[] resumen(String token) throws Exception {
        String r = cuerpo(enviar("GET", "/api/alertas/resumen", token, null).andExpect(status().isOk()));
        assertClaves(r, "$", CLAVES_RESUMEN);
        return new long[] {id(r, "$.activas"), id(r, "$.agotado"), id(r, "$.stockBajo")};
    }

    /** Contrato JSON: las claves presentes (null incluido) son exactamente las del tipo TypeScript. */
    private static void assertClaves(String json, String ruta, Set<String> esperadas) {
        Map<String, Object> objeto = JsonPath.read(json, ruta);
        assertThat(objeto.keySet()).as("claves de %s", ruta).containsExactlyInAnyOrderElementsOf(esperadas);
    }

    // ---------- flujo de reservas y ventas ----------

    @Test
    void reservas_flujoCompleto_stockAlertasKardexYVentaCoherentes() throws Exception {
        // dado: A (M) con 5 y B (L) con 4 unidades, stockMinimo 2 (umbral 2)
        String admin = tokenAdmin();
        String inventario = tokenDe(Rol.INVENTARIO);
        String vendedor = tokenDe(Rol.VENDEDOR);
        long[] ids = productoConDosVariantes(admin);
        long a = ids[1];
        long b = ids[2];
        entrada(inventario, a, 5);
        entrada(inventario, b, 4);

        // VENDEDOR crea 2 reservas del mismo cliente (A x3, B x1): stockReservado sube, A queda en STOCK_BAJO
        String creadas = reservar(vendedor, CLIENTE, CONTACTO, linea(a, 3), linea(b, 1));
        assertThat(JsonPath.<List<?>>read(creadas, "$")).hasSize(2);
        assertClaves(creadas, "$[0]", CLAVES_RESERVA);
        long rA = primero(creadas, "$[?(@.varianteId==" + a + ")].id");
        long rB = primero(creadas, "$[?(@.varianteId==" + b + ")].id");
        assertThat(JsonPath.<String>read(creadas, "$[0].estado")).isEqualTo("ACTIVA");
        assertThat(JsonPath.<String>read(creadas, "$[0].numero")).matches("R-\\d{6}");
        assertThat(JsonPath.<String>read(creadas, "$[0].creadaPor")).isEqualTo("Prueba VENDEDOR");
        assertThat(JsonPath.<Object>read(creadas, "$[0].cerradaPor")).isNull();
        assertThat(fisico(a)).isEqualTo(5);
        assertThat(reservado(a)).isEqualTo(3);
        assertThat(reservado(b)).isEqualTo(1);
        assertThat(tiposAlerta(vendedor, a, "ACTIVA")).containsExactly("STOCK_BAJO");
        assertThat(tiposAlerta(vendedor, b, "ACTIVA")).isEmpty();

        // una segunda reserva de A x2 deja disponible 0: AGOTADO reemplaza a STOCK_BAJO (excluyentes)
        long rA2 = id(reservar(vendedor, CLIENTE, CONTACTO, linea(a, 2)), "$[0].id");
        assertThat(reservado(a)).isEqualTo(5);
        assertThat(tiposAlerta(vendedor, a, "ACTIVA")).containsExactly("AGOTADO");
        assertThat(tiposAlerta(vendedor, a, "ATENDIDA")).containsExactly("STOCK_BAJO");
        enviar("POST", "/api/reservas", vendedor, reservarJson(CLIENTE, CONTACTO, linea(a, 1)))
                .andExpect(status().isUnprocessableContent());
        assertThat(reservado(a)).isEqualTo(5);

        // INVENTARIO extiende rB 24 h; 168 h supera el tope de 7 días desde la creación (422); 0 h es inválido (400)
        String extendida = cuerpo(enviar("POST", "/api/reservas/" + rB + "/extender", inventario, "{\"horas\":24}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVA")));
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota"));
        assertThat(LocalDateTime.parse(JsonPath.read(extendida, "$.venceEn")))
                .isBetween(ahora.plusHours(24).minusMinutes(2), ahora.plusHours(24).plusMinutes(2));
        enviar("POST", "/api/reservas/" + rB + "/extender", inventario, "{\"horas\":168}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("7 días")));
        enviar("POST", "/api/reservas/" + rB + "/extender", inventario, "{\"horas\":0}")
                .andExpect(status().isBadRequest());

        // INVENTARIO cancela rA2 con motivo: LIBERACION, disponible 2 => vuelve STOCK_BAJO y se cierra AGOTADO
        enviar("POST", "/api/reservas/" + rA2 + "/cancelar", inventario, "{\"motivo\":\"abc\"}")
                .andExpect(status().isBadRequest());
        enviar("POST", "/api/reservas/" + rA2 + "/cancelar", inventario, "{\"motivo\":\"cliente desistio\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("cliente desistio"))
                .andExpect(jsonPath("$.cerradaPor").value("Prueba INVENTARIO"))
                .andExpect(jsonPath("$.cerradaEn").isNotEmpty());
        enviar("POST", "/api/reservas/" + rA2 + "/cancelar", inventario, "{\"motivo\":\"otra vez\"}")
                .andExpect(status().isConflict());
        assertThat(reservado(a)).isEqualTo(3);
        assertThat(tiposKardex(inventario, a)).containsExactlyInAnyOrder("ENTRADA", "RESERVA", "RESERVA", "LIBERACION");
        enviar("GET", "/api/movimientos?varianteId=" + a + "&tipo=LIBERACION", inventario, null)
                .andExpect(jsonPath("$.content[0].observacion").value("Cancelada"))
                .andExpect(jsonPath("$.content[0].referenciaTipo").value("RESERVA"))
                .andExpect(jsonPath("$.content[0].referenciaId").value((int) rA2));
        assertThat(tiposAlerta(vendedor, a, "ACTIVA")).containsExactly("STOCK_BAJO");
        long alertaBajoA = idAlerta(vendedor, a, "ACTIVA", "STOCK_BAJO");

        // otro cliente reserva B x1 (rC); convertir clientes distintos => 422 y todo sigue ACTIVA
        long rC = id(reservar(vendedor, "Carlos Ruiz", "3109998888", linea(b, 1)), "$[0].id");
        enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[" + rA + "," + rC + "]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Las reservas deben ser del mismo cliente."));
        assertThat(estadoReserva(rA)).isEqualTo("ACTIVA");
        assertThat(estadoReserva(rC)).isEqualTo("ACTIVA");
        // validaciones previas de la conversión: precio sobre la lista 422, repetidos 400, inexistente 404
        enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[" + rA + "," + rB
                + "],\"precios\":[{\"varianteId\":" + a + ",\"precioUnitario\":60000.00}]}")
                .andExpect(status().isUnprocessableContent());
        enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[" + rA + "," + rA + "]}")
                .andExpect(status().isBadRequest());
        enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[999999999]}")
                .andExpect(status().isNotFound());
        assertThat(estadoReserva(rA)).isEqualTo("ACTIVA");
        assertThat(fisico(a)).isEqualTo(5);

        // convertir las ACTIVAS de Maria (rA, rB) con precio menor a la lista para A: una venta, VENTA_RESERVA
        String venta = cuerpo(enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[" + rA + ","
                + rB + "],\"precios\":[{\"varianteId\":" + a + ",\"precioUnitario\":50000.00}]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.total").value(209900.0)) // 3 x 50000 + 1 x 59900
                .andExpect(jsonPath("$.descuentoTotal").value(29700.0)) // 3 x 9900
                .andExpect(jsonPath("$.lineas.length()").value(2)));
        long ventaId = id(venta, "$.id");
        assertClaves(venta, "$", CLAVES_VENTA);
        assertClaves(venta, "$.lineas[0]", CLAVES_LINEA);
        assertThat(JsonPath.<List<String>>read(venta, "$.reservas")).hasSize(2).allMatch(n -> n.matches("R-\\d{6}"));
        assertThat(JsonPath.<Object>read(venta, "$.lineas[0].costoUnitario")).isNull(); // VENDEDOR no ve costos
        assertThat(JsonPath.<List<Number>>read(venta, "$.lineas[?(@.varianteId==" + a + ")].precioLista").get(0)
                .doubleValue()).isEqualTo(59900.0);
        assertThat(JsonPath.<List<Number>>read(venta, "$.lineas[?(@.varianteId==" + b + ")].precioUnitario").get(0)
                .doubleValue()).isEqualTo(59900.0);
        assertThat(estadoReserva(rA)).isEqualTo("CONVERTIDA");
        assertThat(estadoReserva(rB)).isEqualTo("CONVERTIDA");
        assertThat(estadoReserva(rC)).isEqualTo("ACTIVA");
        assertThat(fisico(a)).isEqualTo(2);
        assertThat(reservado(a)).isZero();
        assertThat(fisico(b)).isEqualTo(3);
        assertThat(reservado(b)).isEqualTo(1);
        enviar("GET", "/api/reservas/" + rA, vendedor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONVERTIDA"))
                .andExpect(jsonPath("$.ventaId").value((int) ventaId))
                .andExpect(jsonPath("$.ventaNumero").value(String.format("V-%06d", ventaId)));
        enviar("GET", "/api/ventas/" + ventaId, vendedor, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas.length()").value(2));
        enviar("GET", "/api/movimientos?varianteId=" + a + "&tipo=VENTA_RESERVA", inventario, null)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].referenciaTipo").value("VENTA"))
                .andExpect(jsonPath("$.content[0].referenciaId").value((int) ventaId))
                .andExpect(jsonPath("$.content[0].cantidad").value(3));
        enviar("POST", "/api/ventas/desde-reservas", vendedor, "{\"reservaIds\":[" + rA + "]}")
                .andExpect(status().isConflict());
        // una sola alerta ACTIVA por variante y tipo: A sigue en STOCK_BAJO con la misma alerta; B (disp 2) abre la suya
        assertThat(idAlerta(vendedor, a, "ACTIVA", "STOCK_BAJO")).isEqualTo(alertaBajoA);
        assertThat(tiposAlerta(vendedor, b, "ACTIVA")).containsExactly("STOCK_BAJO");

        // anular la venta (ADMIN): repone físico y las reservas siguen CONVERTIDA
        enviar("POST", "/api/ventas/" + ventaId + "/anular", admin, "{\"motivo\":\"cliente no recogio\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"));
        assertThat(fisico(a)).isEqualTo(5);
        assertThat(fisico(b)).isEqualTo(4);
        assertThat(estadoReserva(rA)).isEqualTo("CONVERTIDA");
        assertThat(estadoReserva(rB)).isEqualTo("CONVERTIDA");
        assertThat(tiposKardex(inventario, a)).contains("ANULACION_VENTA", "VENTA_RESERVA");
        assertThat(tiposAlerta(vendedor, a, "ACTIVA")).isEmpty(); // disponible 5 > umbral: cierre automático

        // desactivar B con el apartado ACTIVO de Carlos => 409 con SKU, apartados y unidades
        String skuB = jdbc.queryForObject("SELECT sku FROM variante WHERE id = ?", String.class, b);
        enviar("PATCH", "/api/variantes/" + b + "/estado", inventario, "{\"activo\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La variante " + skuB + " tiene 1 apartados activos (1 unidades)."));
        // al cancelar el apartado la desactivación procede
        enviar("POST", "/api/reservas/" + rC + "/cancelar", vendedor, "{\"motivo\":\"cliente cambio de idea\"}")
                .andExpect(status().isOk());
        enviar("PATCH", "/api/variantes/" + b + "/estado", inventario, "{\"activo\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        // listado paginado con el contrato del frontend
        String pagina = cuerpo(enviar("GET", "/api/reservas?estado=CONVERTIDA&varianteId=" + a, vendedor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)));
        assertClaves(pagina, "$", CLAVES_PAGINA);
        assertClaves(pagina, "$.page", CLAVES_META);
        assertClaves(pagina, "$.content[0]", CLAVES_RESERVA);
    }

    // ---------- alertas ----------

    @Test
    void alertas_stockBajoAgotadoExcluyentesUnaActivaCierreAutomaticoYAtender_respetanLasReglas() throws Exception {
        // dado: variante con stockMinimo 2 y 6 unidades; línea base del resumen
        String admin = tokenAdmin();
        String inventario = tokenDe(Rol.INVENTARIO);
        String vendedor = tokenDe(Rol.VENDEDOR);
        long a = productoConDosVariantes(admin)[1];
        long[] base = resumen(inventario);
        entrada(inventario, a, 6);
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).isEmpty();

        // disponible 2 (= umbral): STOCK_BAJO, con el contrato de AlertaResponse y el resumen +1
        ventaDirecta(vendedor, a, 4);
        String bajas = alertas(inventario, a, "ACTIVA");
        assertThat(JsonPath.<List<?>>read(bajas, "$")).hasSize(1);
        assertClaves(bajas, "$[0]", CLAVES_ALERTA);
        assertThat(JsonPath.<String>read(bajas, "$[0].tipo")).isEqualTo("STOCK_BAJO");
        assertThat(JsonPath.<Integer>read(bajas, "$[0].stockDisponible")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(bajas, "$[0].umbral")).isEqualTo(2);
        assertThat(JsonPath.<Object>read(bajas, "$[0].atendidaEn")).isNull();
        assertThat(JsonPath.<Object>read(bajas, "$[0].atendidaPor")).isNull();
        long[] r1 = resumen(inventario);
        assertThat(r1[0] - base[0]).isEqualTo(1);
        assertThat(r1[2] - base[2]).isEqualTo(1);
        assertThat(r1[1] - base[1]).isZero();

        // otro evento con la condición vigente: misma alerta, sin duplicar
        long idBajo = id(bajas, "$[0].id");
        ventaDirecta(vendedor, a, 1);
        assertThat(idAlerta(inventario, a, "ACTIVA", "STOCK_BAJO")).isEqualTo(idBajo);
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).hasSize(1);

        // disponible 0: AGOTADO; STOCK_BAJO se cierra solo (atendidaPor null)
        ventaDirecta(vendedor, a, 1);
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).containsExactly("AGOTADO");
        String cerradas = alertas(inventario, a, "ATENDIDA");
        assertThat(JsonPath.<String>read(cerradas, "$[0].tipo")).isEqualTo("STOCK_BAJO");
        assertThat(JsonPath.<Object>read(cerradas, "$[0].atendidaEn")).isNotNull();
        assertThat(JsonPath.<Object>read(cerradas, "$[0].atendidaPor")).isNull();
        long[] r2 = resumen(inventario);
        assertThat(r2[0] - base[0]).isEqualTo(1);
        assertThat(r2[1] - base[1]).isEqualTo(1);
        assertThat(r2[2] - base[2]).isZero();

        // reponer: cierre automático de AGOTADO
        entrada(inventario, a, 10);
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).isEmpty();
        assertThat(resumen(inventario)).containsExactly(base);

        // subir el umbral por encima del disponible (10 < 15) abre STOCK_BAJO (evento UmbralCambiado)
        enviar("PUT", "/api/variantes/" + a, inventario, "{\"stockMinimo\":15}").andExpect(status().isOk());
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).containsExactly("STOCK_BAJO");
        long idNueva = idAlerta(inventario, a, "ACTIVA", "STOCK_BAJO");
        assertThat(idNueva).isNotEqualTo(idBajo);
        assertThat(JsonPath.<Integer>read(alertas(inventario, a, "ACTIVA"), "$[0].umbral")).isEqualTo(15);

        // atender: VENDEDOR 403; INVENTARIO 200 (ATENDIDA con su nombre); repetir 409; inexistente 404
        enviar("POST", "/api/alertas/" + idNueva + "/atender", vendedor, null).andExpect(status().isForbidden());
        String atendida = cuerpo(enviar("POST", "/api/alertas/" + idNueva + "/atender", inventario, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.atendidaPor").value("Prueba INVENTARIO"))
                .andExpect(jsonPath("$.atendidaEn").isNotEmpty()));
        assertClaves(atendida, "$", CLAVES_ALERTA);
        enviar("POST", "/api/alertas/" + idNueva + "/atender", admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La alerta ya fue atendida."));
        enviar("POST", "/api/alertas/999999999/atender", admin, null).andExpect(status().isNotFound());
        assertThat(tiposAlerta(inventario, a, "ACTIVA")).isEmpty();

        // ADMIN también atiende; los filtros del listado devuelven solo lo pedido
        ventaDirecta(vendedor, a, 10); // disponible 0 => AGOTADO nuevo
        long idAgotado = idAlerta(admin, a, "ACTIVA", "AGOTADO");
        enviar("POST", "/api/alertas/" + idAgotado + "/atender", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
        String agotadas = cuerpo(enviar("GET", "/api/alertas?tipo=AGOTADO&estado=ATENDIDA&size=100", vendedor, null)
                .andExpect(status().isOk()));
        assertClaves(agotadas, "$", CLAVES_PAGINA);
        assertClaves(agotadas, "$.page", CLAVES_META);
        assertThat(JsonPath.<List<String>>read(agotadas, "$.content[*].tipo")).containsOnly("AGOTADO");
        assertThat(JsonPath.<List<String>>read(agotadas, "$.content[*].estado")).containsOnly("ATENDIDA");
    }

    // ---------- matriz de roles ----------

    @Test
    void matrizRoles_reservasYAlertas_respetanLaMatrizDePermisos() throws Exception {
        String admin = tokenAdmin();
        String inventario = tokenDe(Rol.INVENTARIO);
        String vendedor = tokenDe(Rol.VENDEDOR);
        String contabilidad = tokenDe(Rol.CONTABILIDAD);
        long[] ids = productoConDosVariantes(admin);
        long a = ids[1];
        long b = ids[2];
        entrada(admin, a, 10);
        entrada(admin, b, 2); // disponible 2 = umbral => alerta STOCK_BAJO activa
        long reserva = id(reservar(vendedor, CLIENTE, CONTACTO, linea(a, 1)), "$[0].id");
        long alerta = idAlerta(admin, b, "ACTIVA", "STOCK_BAJO");
        String nueva = reservarJson(CLIENTE, CONTACTO, linea(a, 1));
        String convertir = "{\"reservaIds\":[" + reserva + "]}";

        // CONTABILIDAD: 403 en reservas y alertas (lectura y escritura)
        enviar("GET", "/api/reservas", contabilidad, null).andExpect(status().isForbidden());
        enviar("GET", "/api/reservas/" + reserva, contabilidad, null).andExpect(status().isForbidden());
        enviar("POST", "/api/reservas", contabilidad, nueva).andExpect(status().isForbidden());
        enviar("POST", "/api/reservas/" + reserva + "/cancelar", contabilidad, "{\"motivo\":\"motivo valido\"}")
                .andExpect(status().isForbidden());
        enviar("POST", "/api/reservas/" + reserva + "/extender", contabilidad, "{\"horas\":24}")
                .andExpect(status().isForbidden());
        enviar("POST", "/api/ventas/desde-reservas", contabilidad, convertir).andExpect(status().isForbidden());
        enviar("GET", "/api/alertas", contabilidad, null).andExpect(status().isForbidden());
        enviar("GET", "/api/alertas/resumen", contabilidad, null).andExpect(status().isForbidden());
        enviar("POST", "/api/alertas/" + alerta + "/atender", contabilidad, null).andExpect(status().isForbidden());
        assertThat(estadoReserva(reserva)).isEqualTo("ACTIVA");

        // INVENTARIO: 403 al convertir; sí reserva, extiende y cancela; lee y atiende alertas
        enviar("POST", "/api/ventas/desde-reservas", inventario, convertir).andExpect(status().isForbidden());
        enviar("POST", "/api/reservas/" + reserva + "/extender", inventario, "{\"horas\":24}")
                .andExpect(status().isOk());
        long deInventario = id(reservar(inventario, CLIENTE, CONTACTO, linea(a, 1)), "$[0].id");
        enviar("POST", "/api/reservas/" + deInventario + "/cancelar", inventario, "{\"motivo\":\"motivo valido\"}")
                .andExpect(status().isOk());
        enviar("GET", "/api/alertas", inventario, null).andExpect(status().isOk());
        assertThat(estadoReserva(reserva)).isEqualTo("ACTIVA");

        // VENDEDOR: 403 al atender; sí lee alertas, reserva, extiende y convierte
        enviar("POST", "/api/alertas/" + alerta + "/atender", vendedor, null).andExpect(status().isForbidden());
        enviar("GET", "/api/alertas", vendedor, null).andExpect(status().isOk());
        enviar("GET", "/api/alertas/resumen", vendedor, null).andExpect(status().isOk());
        enviar("GET", "/api/reservas", vendedor, null).andExpect(status().isOk());
        enviar("POST", "/api/reservas/" + reserva + "/extender", vendedor, "{\"horas\":24}")
                .andExpect(status().isOk());
        enviar("POST", "/api/ventas/desde-reservas", vendedor, convertir).andExpect(status().isCreated());

        // ADMIN convierte y atiende
        long paraAdmin = id(reservar(admin, CLIENTE, CONTACTO, linea(a, 1)), "$[0].id");
        enviar("POST", "/api/ventas/desde-reservas", admin, "{\"reservaIds\":[" + paraAdmin + "]}")
                .andExpect(status().isCreated());
        enviar("POST", "/api/alertas/" + alerta + "/atender", admin, null).andExpect(status().isOk());

        // sin token: 401
        mvc.perform(get("/api/reservas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
    }
}
