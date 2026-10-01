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
        String token = login("  Admin.Prueba@Livorno.test ".trim(), "ClaveAdminPrueba123!");

        mvc.perform(get("/api/auth/yo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin.prueba@livorno.test"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
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
}
