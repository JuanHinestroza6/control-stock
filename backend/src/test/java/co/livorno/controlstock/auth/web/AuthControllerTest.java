package co.livorno.controlstock.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.dto.UsuarioSesionResponse;
import co.livorno.controlstock.auth.servicio.AuthService;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean UsuarioRepository usuarioRepository;

    private static final String CUERPO = "{\"email\":\"ana@livorno.test\",\"password\":\"secreta\"}";

    @Test
    void login_credencialesValidas_responde200ConContrato() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(LoginResponse.de("tok", Instant.parse("2026-10-02T00:00:00Z"),
                new UsuarioSesionResponse(1L, "Ana", "ana@livorno.test", "VENDEDOR")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-02T00:00:00Z"))
                .andExpect(jsonPath("$.usuario.id").value(1))
                .andExpect(jsonPath("$.usuario.rol").value("VENDEDOR"));
    }

    @Test
    void login_credencialesInvalidas_responde401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("x"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void login_cuerpoInvalido_responde400ConErrores() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo=='email')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='password')]").exists());
    }

    @Test
    void yo_conUsuarioAutenticado_responde200() throws Exception {
        var principal = new UsuarioAutenticado(5L, "ana@livorno.test", "Ana", "ADMIN");
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mvc.perform(get("/api/auth/yo").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("ana@livorno.test"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }
}
