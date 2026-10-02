package co.livorno.controlstock.usuario;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Administración de usuarios y seguridad por rol de punta a punta (ciclo de la contraseña temporal incluido). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioSeguridadIntegracionTest {

    private static final String CLAVE = "Clave123!Segura";
    private static final String TEMPORAL = "Temporal123!Uno";
    private static final String NUEVA = "NuevaClave123!Dos";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired LimitadorIntentosLogin limitador;

    private final List<Long> creados = new ArrayList<>();
    private String sufijo;

    @BeforeEach
    void preparar() {
        limitador.limpiar();
        sufijo = Long.toString(System.nanoTime(), 36);
    }

    @AfterEach
    void limpiar() {
        for (Long id : creados) {
            usuarios.deleteById(id);
        }
    }

    // ---------- utilidades ----------

    private String email(String prefijo) {
        return prefijo + "." + sufijo + "@livorno.test";
    }

    private String tokenAdminBase() throws Exception {
        return login("admin.prueba@livorno.test", "ClaveAdminPrueba123!");
    }

    private Usuario crearDirecto(Rol rol, String prefijo) {
        Usuario u = usuarios.save(new Usuario("Prueba " + prefijo, email(prefijo), encoder.encode(CLAVE), rol));
        creados.add(u.getId());
        return u;
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = intentarLogin(email, password)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private ResultActions intentarLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions enviar(String metodo, String ruta, String token, String json) throws Exception {
        MockHttpServletRequestBuilder req = switch (metodo) {
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

    private long crearViaApi(String tokenAdmin, String prefijo, Rol rol) throws Exception {
        String json = "{\"nombre\":\"Vendedora " + prefijo + "\",\"email\":\"" + email(prefijo)
                + "\",\"rol\":\"" + rol + "\",\"passwordTemporal\":\"" + TEMPORAL + "\"}";
        String cuerpo = enviar("POST", "/api/usuarios", tokenAdmin, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.debeCambiarPassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.passwordTemporal").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        creados.add(id);
        return id;
    }

    // ---------- flujo completo ----------

    @Test
    void flujoCompleto_adminCreaVendedorTemporalCambiaRestablecidoYDesactivado_respetaCadaEstado() throws Exception {
        // dado: un ADMIN que crea un VENDEDOR con contraseña temporal
        String admin = tokenAdminBase();
        long id = crearViaApi(admin, "vendedor", Rol.VENDEDOR);

        // cuando: el VENDEDOR inicia sesión con la temporal
        String cuerpoLogin = intentarLogin(email("vendedor"), TEMPORAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.rol").value("VENDEDOR"))
                .andExpect(jsonPath("$.usuario.debeCambiarPassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String tokenTemporal = JsonPath.read(cuerpoLogin, "$.token");

        // entonces: cualquier endpoint de negocio da 403 con codigo; /auth/yo funciona
        enviar("GET", "/api/productos", tokenTemporal, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        enviar("GET", "/api/auth/yo", tokenTemporal, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));

        // cuando: cambia su contraseña (el token viejo debe tener iat anterior al cambio: precisión de segundos)
        Thread.sleep(1100);
        String cuerpoCambio = enviar("PUT", "/api/auth/yo/password", tokenTemporal,
                "{\"actual\":\"" + TEMPORAL + "\",\"nueva\":\"" + NUEVA + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarPassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String tokenNuevo = JsonPath.read(cuerpoCambio, "$.token");

        // entonces: el viejo da 401, el nuevo lee productos y no puede crear
        enviar("GET", "/api/auth/yo", tokenTemporal, null).andExpect(status().isUnauthorized());
        enviar("GET", "/api/productos", tokenNuevo, null).andExpect(status().isOk());
        enviar("POST", "/api/productos", tokenNuevo, "{\"codigo\":\"VX-1\",\"nombre\":\"N\",\"categoriaId\":1,\"precioCompra\":1.00,\"precioVenta\":2.00}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist());

        // cuando: el ADMIN restablece la contraseña
        Thread.sleep(1100);
        enviar("PUT", "/api/usuarios/" + id + "/password", admin,
                "{\"passwordTemporal\":\"" + TEMPORAL + "\"}").andExpect(status().isNoContent());

        // entonces: token revocado y vuelve a exigir cambio
        enviar("GET", "/api/auth/yo", tokenNuevo, null).andExpect(status().isUnauthorized());
        intentarLogin(email("vendedor"), NUEVA).andExpect(status().isUnauthorized());
        intentarLogin(email("vendedor"), TEMPORAL).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarPassword").value(true));

        // cuando: el ADMIN lo desactiva; entonces su login da 401
        enviar("PATCH", "/api/usuarios/" + id + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        intentarLogin(email("vendedor"), TEMPORAL).andExpect(status().isUnauthorized());
    }

    // ---------- reglas de autoprotección ----------

    @Test
    void desactivarse_siendoAdmin_devuelve422() throws Exception {
        Usuario admin = crearDirecto(Rol.ADMIN, "admin2");
        String token = login(admin.getEmail(), CLAVE);

        enviar("PATCH", "/api/usuarios/" + admin.getId() + "/estado", token, "{\"activo\":false}")
                .andExpect(status().isUnprocessableContent());
        assertThat(usuarios.findById(admin.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void cambiarseElRol_siendoAdmin_devuelve422() throws Exception {
        Usuario admin = crearDirecto(Rol.ADMIN, "admin2");
        String token = login(admin.getEmail(), CLAVE);

        enviar("PUT", "/api/usuarios/" + admin.getId(), token,
                "{\"nombre\":\"X\",\"email\":\"" + admin.getEmail() + "\",\"rol\":\"VENDEDOR\"}")
                .andExpect(status().isUnprocessableContent());
        assertThat(usuarios.findById(admin.getId()).orElseThrow().getRol()).isEqualTo(Rol.ADMIN);
    }

    @Test
    void restablecerPasswordPropia_siendoAdmin_devuelve422() throws Exception {
        Usuario admin = crearDirecto(Rol.ADMIN, "admin2");
        String token = login(admin.getEmail(), CLAVE);

        enviar("PUT", "/api/usuarios/" + admin.getId() + "/password", token,
                "{\"passwordTemporal\":\"" + TEMPORAL + "\"}").andExpect(status().isUnprocessableContent());
    }

    @Test
    void degradarYDesactivarAOtroAdmin_conOtroAdminActivo_esPermitido() throws Exception {
        Usuario otro = crearDirecto(Rol.ADMIN, "admin3");
        String admin = tokenAdminBase();

        enviar("PUT", "/api/usuarios/" + otro.getId(), admin,
                "{\"nombre\":\"Otro\",\"email\":\"" + otro.getEmail() + "\",\"rol\":\"INVENTARIO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("INVENTARIO"));
        enviar("PATCH", "/api/usuarios/" + otro.getId() + "/estado", admin, "{\"activo\":false}")
                .andExpect(status().isOk());
    }

    @Test
    void crearUsuario_conCorreoDuplicadoOPasswordCorta_devuelve409Y400() throws Exception {
        String admin = tokenAdminBase();
        crearViaApi(admin, "dup", Rol.VENDEDOR);

        enviar("POST", "/api/usuarios", admin, "{\"nombre\":\"D\",\"email\":\"" + email("dup").toUpperCase()
                + "\",\"rol\":\"VENDEDOR\",\"passwordTemporal\":\"" + TEMPORAL + "\"}")
                .andExpect(status().isConflict());
        enviar("POST", "/api/usuarios", admin, "{\"nombre\":\"D\",\"email\":\"" + email("otro")
                + "\",\"rol\":\"VENDEDOR\",\"passwordTemporal\":\"corta\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("passwordTemporal"));
    }

    // ---------- permisos por rol ----------

    @Test
    void usuarios_conRolesDistintosDeAdmin_devuelve403EnTodosLosEndpoints() throws Exception {
        long idObjetivo = crearDirecto(Rol.VENDEDOR, "objetivo").getId();
        for (Rol rol : List.of(Rol.INVENTARIO, Rol.VENDEDOR, Rol.CONTABILIDAD)) {
            Usuario u = crearDirecto(rol, rol.name().toLowerCase());
            String t = login(u.getEmail(), CLAVE);
            String base = "/api/usuarios";
            enviar("GET", base, t, null).andExpect(status().isForbidden());
            enviar("GET", base + "/" + idObjetivo, t, null).andExpect(status().isForbidden());
            enviar("POST", base, t, "{\"nombre\":\"N\",\"email\":\"n@x.co\",\"rol\":\"ADMIN\",\"passwordTemporal\":\""
                    + TEMPORAL + "\"}").andExpect(status().isForbidden());
            enviar("PUT", base + "/" + idObjetivo, t,
                    "{\"nombre\":\"N\",\"email\":\"n@x.co\",\"rol\":\"ADMIN\"}").andExpect(status().isForbidden());
            enviar("PATCH", base + "/" + idObjetivo + "/estado", t, "{\"activo\":false}")
                    .andExpect(status().isForbidden());
            enviar("PUT", base + "/" + idObjetivo + "/password", t,
                    "{\"passwordTemporal\":\"" + TEMPORAL + "\"}").andExpect(status().isForbidden());
        }
        assertThat(usuarios.findById(idObjetivo).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void usuarios_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void listarUsuarios_comoAdmin_devuelvePaginaConContratoDelFrontend() throws Exception {
        crearDirecto(Rol.VENDEDOR, "listado");

        mvc.perform(get("/api/usuarios?q=listado." + sufijo).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenAdminBase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(email("listado")))
                .andExpect(jsonPath("$.content[0].creadoEn").exists())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}
