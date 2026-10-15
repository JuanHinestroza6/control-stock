package co.livorno.controlstock.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
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

/** Flujo de punta a punta del catálogo (categoría, producto, variantes, matriz, estados y permisos por rol). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoIntegracionTest {

    private static final String CLAVE = "Clave123!";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimitadorIntentosLogin limitador;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final List<Long> productosCreados = new ArrayList<>();
    private final List<Long> categoriasCreadas = new ArrayList<>();
    private String sufijo;

    @BeforeEach
    void preparar() {
        limitador.limpiar();
        sufijo = Long.toString(System.nanoTime(), 36);
    }

    @AfterEach
    void limpiar() {
        for (Long id : productosCreados) {
            for (String t : new String[] {"alerta", "analitica_variante", "reserva"}) {
                jdbc.update("DELETE FROM " + t + " WHERE variante_id IN (SELECT id FROM variante WHERE producto_id = ?)", id);
            }
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", id);
            jdbc.update("DELETE FROM producto WHERE id = ?", id);
        }
        for (Long id : categoriasCreadas) {
            jdbc.update("DELETE FROM categoria WHERE id = ?", id);
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

    private long crearCategoria(String token) throws Exception {
        String cuerpo = enviar("POST", "/api/categorias", token, "{\"nombre\":\"Cat " + sufijo + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        categoriasCreadas.add(id);
        return id;
    }

    private long crearProducto(String token, long categoriaId) throws Exception {
        String json = "{\"codigo\":\"it-" + sufijo + "\",\"nombre\":\"Camiseta IT\",\"categoriaId\":" + categoriaId
                + ",\"precioCompra\":25000.00,\"precioVenta\":59900.00}";
        String cuerpo = enviar("POST", "/api/productos", token, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("IT-" + sufijo.toUpperCase()))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.totalVariantes").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        productosCreados.add(id);
        return id;
    }

    private static String generarJson(String tallas, String colores) {
        return "{\"tallas\":" + tallas + ",\"colores\":" + colores + ",\"stockMinimo\":3}";
    }

    private long productoConVariantes(String token) throws Exception {
        long id = crearProducto(token, crearCategoria(token));
        enviar("POST", "/api/productos/" + id + "/variantes/generar", token,
                generarJson("[\"S\",\"M\",\"L\"]", "[\"Negro\",\"Blanco\"]")).andExpect(status().isCreated());
        return id;
    }

    private long primeraVariante(long productoId) {
        return jdbc.queryForObject("SELECT MIN(id) FROM variante WHERE producto_id = ?", Long.class, productoId);
    }

    private int contarActivas(long productoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM variante WHERE producto_id = ? AND activo = TRUE",
                Integer.class, productoId);
    }

    // ---------- flujo principal ----------

    @Test
    void catalogo_flujoCompletoAdmin_generaMatrizIdempotenteYCascadaDeEstado() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        // generar 3 x 2
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"S\",\"M\",\"L\"]", "[\"Negro\",\"Blanco\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadas.length()").value(6))
                .andExpect(jsonPath("$.omitidas.length()").value(0));

        // matriz: 6 celdas en 0 y AGOTADO
        String matriz = enviar("GET", "/api/productos/" + productoId + "/matriz", admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tallas.length()").value(3))
                .andExpect(jsonPath("$.colores.length()").value(2))
                .andExpect(jsonPath("$.celdas.length()").value(6))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(matriz, "$.celdas[*].stockFisico")).containsOnly(0);
        assertThat(JsonPath.<List<Integer>>read(matriz, "$.celdas[*].stockReservado")).containsOnly(0);
        assertThat(JsonPath.<List<Integer>>read(matriz, "$.celdas[*].stockDisponible")).containsOnly(0);
        assertThat(JsonPath.<List<String>>read(matriz, "$.celdas[*].estadoStock")).containsOnly("AGOTADO");
        assertThat(JsonPath.<List<Boolean>>read(matriz, "$.celdas[*].activo")).containsOnly(true);
        assertThat(JsonPath.<List<String>>read(matriz, "$.celdas[*].sku")).doesNotHaveDuplicates()
                .allMatch(s -> s.startsWith("IT-" + sufijo.toUpperCase() + "-"));

        // regenerar es idempotente (aunque cambie el formato del color)
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"S\",\"M\",\"L\"]", "[\" negro \",\"BLANCO\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadas.length()").value(0))
                .andExpect(jsonPath("$.omitidas.length()").value(6))
                .andExpect(jsonPath("$.omitidas[0].motivo").value("EXISTE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM variante WHERE producto_id = ?", Integer.class,
                productoId)).isEqualTo(6);

        // desactivar producto desactiva sus variantes
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.totalVariantes").value(0));
        assertThat(contarActivas(productoId)).isZero();
        enviar("GET", "/api/productos/" + productoId + "/matriz", admin, null)
                .andExpect(jsonPath("$.celdas.length()").value(0));
        enviar("GET", "/api/productos/" + productoId + "/matriz?incluirInactivas=true", admin, null)
                .andExpect(jsonPath("$.celdas.length()").value(6));

        // reactivar el producto NO reactiva las variantes
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.totalVariantes").value(0));
        assertThat(contarActivas(productoId)).isZero();

        // regenerar tras reactivar: las 6 aparecen como EXISTE_INACTIVA y siguen inactivas
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"S\",\"M\",\"L\"]", "[\"Negro\",\"Blanco\"]"))
                .andExpect(jsonPath("$.creadas.length()").value(0))
                .andExpect(jsonPath("$.omitidas[0].motivo").value("EXISTE_INACTIVA"));
        assertThat(contarActivas(productoId)).isZero();
    }

    // ---------- permisos por rol ----------

    @Test
    void catalogo_vendedor_leeSinCostoYNoPuedeEscribir() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        long varianteId = primeraVariante(productoId);
        String vendedor = tokenDe(Rol.VENDEDOR);

        enviar("GET", "/api/productos/" + productoId, vendedor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioCompra").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.precioVenta").value(59900.00));
        enviar("GET", "/api/productos?size=5", vendedor, null).andExpect(status().isOk());
        enviar("GET", "/api/productos/" + productoId + "/matriz", vendedor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.celdas.length()").value(6));
        enviar("GET", "/api/productos/" + productoId + "/variantes", vendedor, null)
                .andExpect(status().isOk());
        enviar("GET", "/api/categorias", vendedor, null).andExpect(status().isOk());

        String producto = "{\"codigo\":\"X" + sufijo + "\",\"nombre\":\"N\",\"categoriaId\":1,\"precioCompra\":1,"
                + "\"precioVenta\":2}";
        String estado = "{\"activo\":false}";
        enviar("POST", "/api/categorias", vendedor, "{\"nombre\":\"Zzz " + sufijo + "\"}")
                .andExpect(status().isForbidden());
        enviar("POST", "/api/productos", vendedor, producto).andExpect(status().isForbidden());
        enviar("PUT", "/api/productos/" + productoId, vendedor,
                "{\"nombre\":\"N\",\"categoriaId\":1,\"precioCompra\":1,\"precioVenta\":2,"
                        + "\"leadTimeDias\":15,\"loteMinimo\":1}").andExpect(status().isForbidden());
        enviar("PATCH", "/api/productos/" + productoId + "/estado", vendedor, estado)
                .andExpect(status().isForbidden());
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", vendedor,
                generarJson("[\"XL\"]", "[\"Rojo\"]")).andExpect(status().isForbidden());
        enviar("PUT", "/api/variantes/" + varianteId, vendedor, "{\"stockMinimo\":9}")
                .andExpect(status().isForbidden());
        enviar("PATCH", "/api/variantes/" + varianteId + "/estado", vendedor, estado)
                .andExpect(status().isForbidden());

        // nada cambió
        assertThat(contarActivas(productoId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT stock_minimo FROM variante WHERE id = ?", Integer.class, varianteId))
                .isEqualTo(3);
    }

    @Test
    void catalogo_contabilidad_veCostoPeroNoEscribe() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        String contabilidad = tokenDe(Rol.CONTABILIDAD);

        enviar("GET", "/api/productos/" + productoId, contabilidad, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioCompra").value(25000.00));
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", contabilidad,
                generarJson("[\"XL\"]", "[\"Rojo\"]")).andExpect(status().isForbidden());
        enviar("PATCH", "/api/productos/" + productoId + "/estado", contabilidad, "{\"activo\":false}")
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogo_inventario_puedeCrearYVeCosto() throws Exception {
        String inventario = tokenDe(Rol.INVENTARIO);
        long productoId = productoConVariantes(inventario);

        enviar("GET", "/api/productos/" + productoId, inventario, null)
                .andExpect(jsonPath("$.precioCompra").value(25000.00));
    }

    @Test
    void catalogo_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/productos/1/variantes/generar").contentType(MediaType.APPLICATION_JSON)
                .content(generarJson("[\"S\"]", "[\"Negro\"]"))).andExpect(status().isUnauthorized());
    }

    // ---------- casos borde ----------

    @Test
    void desactivarProducto_conVarianteApartada_responde409YNoDesactivaNada() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        jdbc.update("UPDATE variante SET stock_fisico = 5, stock_reservado = 2 WHERE id = ?",
                primeraVariante(productoId));

        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isConflict());

        assertThat(contarActivas(productoId)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT activo FROM producto WHERE id = ?", Boolean.class, productoId))
                .isTrue();
    }

    @Test
    void desactivarVariante_conReservado_responde409() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        long varianteId = primeraVariante(productoId);
        jdbc.update("UPDATE variante SET stock_fisico = 4, stock_reservado = 1 WHERE id = ?", varianteId);

        enviar("PATCH", "/api/variantes/" + varianteId + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isConflict());
    }

    @Test
    void activarVariante_conProductoInactivo_responde422() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        long varianteId = primeraVariante(productoId);
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isOk());

        enviar("PATCH", "/api/variantes/" + varianteId + "/estado", admin, "{\"activo\":true}")
                .andExpect(status().is(422));
    }

    @Test
    void activarVariante_conProductoActivo_laReactivaIndividualmente() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        long varianteId = primeraVariante(productoId);
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}");
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":true}");

        enviar("PATCH", "/api/variantes/" + varianteId + "/estado", admin, "{\"activo\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));

        assertThat(contarActivas(productoId)).isEqualTo(1);
    }

    @Test
    void generarVariantes_enProductoInactivo_responde422() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}");

        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"Negro\"]")).andExpect(status().is(422));
    }

    @Test
    void generarVariantes_tallaUnicaConOtrasTallas_responde422YNoCreaNada() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"UNICA\",\"M\"]", "[\"Negro\"]")).andExpect(status().is(422));
        // combinada con una talla ya existente del producto
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"Negro\"]")).andExpect(status().isCreated());
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"UNICA\"]", "[\"Negro\"]")).andExpect(status().is(422));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM variante WHERE producto_id = ?", Integer.class,
                productoId)).isEqualTo(1);
    }

    @Test
    void generarVariantes_coloresQueColisionanEnSku_obtienenSkuDistintoConSufijo() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        // "Azul cielo" y "Azul claro" abrevian ambos a AZU
        String cuerpo = enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"Azul cielo\",\"Azul claro\"]"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creadas.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(cuerpo, "$.creadas[*].sku")).doesNotHaveDuplicates();
    }

    @Test
    void generarVariantes_cuerpoInvalido_responde400ConErrores() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                "{\"tallas\":[],\"colores\":[\"  \"],\"stockMinimo\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                "{\"tallas\":[\"XXXL\"],\"colores\":[\"Negro\"],\"stockMinimo\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogo_recursosInexistentes_responden404() throws Exception {
        String admin = tokenAdmin();

        enviar("GET", "/api/productos/999999999", admin, null).andExpect(status().isNotFound());
        enviar("GET", "/api/productos/999999999/matriz", admin, null).andExpect(status().isNotFound());
        enviar("POST", "/api/productos/999999999/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"Negro\"]")).andExpect(status().isNotFound());
        enviar("PUT", "/api/variantes/999999999", admin, "{\"stockMinimo\":1}").andExpect(status().isNotFound());
    }

    @Test
    void crearProducto_codigoDuplicadoEnOtraCapitalizacion_responde409() throws Exception {
        String admin = tokenAdmin();
        long categoriaId = crearCategoria(admin);
        crearProducto(admin, categoriaId);

        String json = "{\"codigo\":\"IT-" + sufijo.toUpperCase() + "\",\"nombre\":\"Otro\",\"categoriaId\":"
                + categoriaId + ",\"precioCompra\":1.00,\"precioVenta\":2.00}";
        enviar("POST", "/api/productos", admin, json).andExpect(status().isConflict());
    }

    @Test
    void actualizarProducto_conPrecioVentaCero_responde400() throws Exception {
        String admin = tokenAdmin();
        long categoriaId = crearCategoria(admin);
        long productoId = crearProducto(admin, categoriaId);

        enviar("PUT", "/api/productos/" + productoId, admin, "{\"nombre\":\"N\",\"categoriaId\":" + categoriaId
                + ",\"precioCompra\":1.00,\"precioVenta\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void actualizarVariante_stockMinimo_sePersisteYNoTocaElStock() throws Exception {
        String admin = tokenAdmin();
        long productoId = productoConVariantes(admin);
        long varianteId = primeraVariante(productoId);

        enviar("PUT", "/api/variantes/" + varianteId, admin, "{\"stockMinimo\":12}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockMinimo").value(12));
        enviar("PUT", "/api/variantes/" + varianteId, admin, "{\"stockMinimo\":-1}")
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, varianteId))
                .isZero();
    }

    @Test
    void generarVariantes_colorConTildeSobreExistenteSinTilde_loOmiteYNoDa409() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));
        String ruta = "/api/productos/" + productoId + "/variantes/generar";
        enviar("POST", ruta, admin, generarJson("[\"M\"]", "[\"Azul\",\"Anil\"]"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creadas.length()").value(2));

        // la collation de la BD ignora tildes: AZÚL = AZUL y AÑIL = ANIL
        enviar("POST", ruta, admin, generarJson("[\"M\"]", "[\"Azúl\",\"Añil\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creadas.length()").value(0))
                .andExpect(jsonPath("$.omitidas.length()").value(2))
                .andExpect(jsonPath("$.omitidas[*].motivo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("EXISTE"))));

        // desactivar: el mismo color con tilde se reporta como EXISTE_INACTIVA
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":false}").andExpect(status().isOk());
        enviar("PATCH", "/api/productos/" + productoId + "/estado", admin, "{\"activo\":true}").andExpect(status().isOk());
        enviar("POST", ruta, admin, generarJson("[\"M\"]", "[\"AZÚL\"]"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creadas.length()").value(0))
                .andExpect(jsonPath("$.omitidas[0].motivo").value("EXISTE_INACTIVA"));
    }

    @Test
    void generarVariantes_dosColoresQueSoloDifierenEnTildeEnLaMismaSolicitud_creaUno() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"Azul\",\"Azúl\"]"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.creadas.length()").value(1));
    }

    @Test
    void generarVariantes_colorConLetrasNoLatinas_responde422() throws Exception {
        String admin = tokenAdmin();
        long productoId = crearProducto(admin, crearCategoria(admin));

        enviar("POST", "/api/productos/" + productoId + "/variantes/generar", admin,
                generarJson("[\"M\"]", "[\"ΑΒΓ\"]")).andExpect(status().is(422));
    }
}
