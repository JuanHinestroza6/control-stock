package co.livorno.controlstock.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
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
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository repo;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private String login(String email, String password) throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    @Test
    void login_adminDePrueba_yoResponde200() throws Exception {
        String token = login("ADMIN.PRUEBA@LIVORNO.TEST", "ClaveAdminPrueba123!");

        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin.prueba@livorno.test"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Autowired co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin limitador;

    @org.junit.jupiter.api.BeforeEach
    void limpiarLimitador() {
        limitador.limpiar();
    }

    private void intentar(String email, String password, int esperado) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(esperado));
    }

    @Test
    void login_tras5FallosSeguidos_el6responde429AunqueLaClaveSeaCorrecta() throws Exception {
        for (int i = 0; i < 5; i++) {
            intentar("admin.prueba@livorno.test", "mala", 401);
        }

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.prueba@livorno.test\",\"password\":\"ClaveAdminPrueba123!\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos fallidos. Intenta de nuevo en 15 minutos."));
    }

    @Test
    void login_emailInexistente_seBloqueaIgualQueUnoExistente() throws Exception {
        for (int i = 0; i < 5; i++) {
            intentar("fantasma@livorno.test", "mala", 401);
        }

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fantasma@livorno.test\",\"password\":\"mala\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Demasiados intentos fallidos. Intenta de nuevo en 15 minutos."));
    }

    @Test
    void login_exitosoTrasFallos_limpiaElContador() throws Exception {
        for (int i = 0; i < 4; i++) {
            intentar("admin.prueba@livorno.test", "mala", 401);
        }
        intentar("admin.prueba@livorno.test", "ClaveAdminPrueba123!", 200);
        for (int i = 0; i < 4; i++) {
            intentar("admin.prueba@livorno.test", "mala", 401);
        }
    }

    @Test
    void actuator_health_esPublicoYSinDetalles() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void actuator_envEInfo_noEstanAccesibles() throws Exception {
        for (String ruta : new String[] {"/actuator/env", "/actuator/info"}) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            String token = login("admin.prueba@livorno.test", "ClaveAdminPrueba123!");
            mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void login_passwordIncorrecta_responde401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.prueba@livorno.test\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void yo_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    void yo_usuarioDesactivadoTrasObtenerToken_responde401() throws Exception {
        Usuario u = repo.save(new Usuario("Temporal", "temporal.desactivar@livorno.test",
                encoder.encode("Clave123!"), Rol.VENDEDOR));
        try {
            String token = login("temporal.desactivar@livorno.test", "Clave123!");
            mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", u.getId());

            mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"temporal.desactivar@livorno.test\",\"password\":\"Clave123!\"}"))
                    .andExpect(status().isUnauthorized());
        } finally {
            repo.deleteById(u.getId());
        }
    }

    @Autowired JwtEncoder jwtEncoder;

    private String tokenPara(String sub, Instant emitido, Instant expira) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(sub).issuedAt(emitido).expiresAt(expira).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void login_respuesta_cumpleContratoDelFrontendYNoExponeHash() throws Exception {
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ADMIN.PRUEBA@livorno.test\",\"password\":\"ClaveAdminPrueba123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.id").isNumber())
                .andExpect(jsonPath("$.usuario.nombre").isString())
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain("$2");
        assertThat(Instant.parse(JsonPath.<String>read(cuerpo, "$.expiraEn"))).isAfter(Instant.now());
    }

    @Test
    void yo_rolCambiadoEnBdTrasEmitirToken_reflejaElRolVigente() throws Exception {
        Usuario u = repo.save(new Usuario("Cambia Rol", "cambia.rol@livorno.test", encoder.encode("Clave123!"),
                Rol.VENDEDOR));
        try {
            String token = login("cambia.rol@livorno.test", "Clave123!");
            mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(jsonPath("$.rol").value("VENDEDOR"));

            jdbc.update("UPDATE usuario SET rol = 'INVENTARIO' WHERE id = ?", u.getId());

            mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value("INVENTARIO"));
        } finally {
            repo.deleteById(u.getId());
        }
    }

    @Test
    void yo_tokenValidoDeUsuarioInexistente_responde401() throws Exception {
        String token = tokenPara("999999999", Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void yo_tokenConSubNoNumerico_responde401() throws Exception {
        String token = tokenPara("no-es-numero", Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void yo_tokenVencido_responde401() throws Exception {
        Instant hace2h = Instant.now().minusSeconds(7200);
        String token = tokenPara("1", hace2h, hace2h.plusSeconds(60));

        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_emailVacioYPasswordVacia_responde400ConErrores() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo=='email')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='password')]").exists());
    }
}
