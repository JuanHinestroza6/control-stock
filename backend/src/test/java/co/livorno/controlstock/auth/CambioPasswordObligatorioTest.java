package co.livorno.controlstock.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(CambioPasswordObligatorioTest.Rutas.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, CambioPasswordObligatorioTest.Rutas.class})
class CambioPasswordObligatorioTest {

    @RestController
    static class Rutas {
        @GetMapping("/api/auth/yo")
        @PreAuthorize("isAuthenticated()")
        String yo() { return "yo"; }

        @PutMapping("/api/auth/yo/password")
        @PreAuthorize("isAuthenticated()")
        String cambiar() { return "ok"; }

        @GetMapping("/api/productos")
        @PreAuthorize("isAuthenticated()")
        String productos() { return "p"; }

        @GetMapping("/api/prueba/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() { return "a"; }
    }

    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository usuarioRepository;

    private static Authentication como(String rol, boolean debeCambiar) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(1L, "u@livorno.test", "U", rol, debeCambiar), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void conCambioPendiente_endpointNormal_responde403ConCodigo() throws Exception {
        mvc.perform(get("/api/productos").with(authentication(como("ADMIN", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"))
                .andExpect(jsonPath("$.detail").value("Debes cambiar tu contraseña temporal antes de continuar."));
    }

    @Test
    void conCambioPendiente_yoYCambioDePassword_estanPermitidos() throws Exception {
        mvc.perform(get("/api/auth/yo").with(authentication(como("VENDEDOR", true)))).andExpect(status().isOk());
        mvc.perform(put("/api/auth/yo/password").with(authentication(como("VENDEDOR", true))))
                .andExpect(status().isOk());
    }

    @Test
    void sinCambioPendiente_accedeNormalmente() throws Exception {
        mvc.perform(get("/api/productos").with(authentication(como("VENDEDOR", false))))
                .andExpect(status().isOk());
    }

    @Test
    void sinCambioPendiente_sinPermiso_siguienteSiendo403SinCodigo() throws Exception {
        mvc.perform(get("/api/prueba/admin").with(authentication(como("VENDEDOR", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").doesNotExist())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void sinToken_sigueResponde401() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }
}
