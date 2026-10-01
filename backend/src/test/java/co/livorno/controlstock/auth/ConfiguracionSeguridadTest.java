package co.livorno.controlstock.auth;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ConfiguracionSeguridadTest.ControllerSoloAdmin.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class,
        ConfiguracionSeguridadTest.ControllerSoloAdmin.class})
class ConfiguracionSeguridadTest {

    @RestController
    static class ControllerSoloAdmin {
        @GetMapping("/api/prueba/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String soloAdmin() { return "ok"; }
    }

    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository usuarioRepository;

    private static UsernamePasswordAuthenticationToken conRol(String rol) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(1L, "u@livorno.test", "U", rol), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void peticion_sinToken_responde401ConProblemDetail() throws Exception {
        mvc.perform(get("/api/prueba/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;

    @Test
    void peticion_tokenConOtroIssuer_responde401() throws Exception {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("otro-emisor").subject("1")
                .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(600)).build();
        String token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/prueba/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.instance").value("/api/prueba/admin"));
    }

    @Test
    void peticion_tokenMalformado_responde401() throws Exception {
        mvc.perform(get("/api/prueba/admin").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void peticion_vendedorContraEndpointDeAdmin_responde403() throws Exception {
        mvc.perform(get("/api/prueba/admin").with(authentication(conRol("VENDEDOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void peticion_admin_responde200() throws Exception {
        mvc.perform(get("/api/prueba/admin").with(authentication(conRol("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void cors_preflightDesdeOrigenPermitido_seAcepta() throws Exception {
        mvc.perform(options("/api/prueba/admin")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void cors_preflightDesdeOtroOrigen_seRechaza() throws Exception {
        mvc.perform(options("/api/prueba/admin")
                        .header(HttpHeaders.ORIGIN, "http://malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, nullValue()));
    }
}
