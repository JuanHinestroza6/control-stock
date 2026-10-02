package co.livorno.controlstock.usuario.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.dto.UsuarioResponse;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.servicio.UsuarioService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UsuarioController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class UsuarioControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static final String CREAR = """
            {"nombre":"Ana","email":"ana@livorno.co","rol":"VENDEDOR","passwordTemporal":"Temporal-12345"}""";
    private static final String ACTUALIZAR = """
            {"nombre":"Ana","email":"ana@livorno.co","rol":"VENDEDOR"}""";
    private static final String PASSWORD = "{\"passwordTemporal\":\"Temporal-12345\"}";
    private static final String ESTADO = "{\"activo\":false}";

    private static UsuarioResponse respuesta() {
        return new UsuarioResponse(5L, "Ana", "ana@livorno.co", Rol.VENDEDOR, true, true,
                LocalDateTime.parse("2026-10-01T10:15:30"), LocalDateTime.parse("2026-10-01T10:15:30"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String cuerpo) {
        return b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void todosLosEndpoints_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(CREAR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void todosLosEndpoints_rolNoAdmin_responde403SinLlamarAlServicio() throws Exception {
        for (String rol : List.of("INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            var a = authentication(como(rol));
            mvc.perform(get("/api/usuarios").with(a)).andExpect(status().isForbidden());
            mvc.perform(get("/api/usuarios/5").with(a)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/usuarios").with(a), CREAR)).andExpect(status().isForbidden());
            mvc.perform(json(put("/api/usuarios/5").with(a), ACTUALIZAR)).andExpect(status().isForbidden());
            mvc.perform(json(patch("/api/usuarios/5/estado").with(a), ESTADO)).andExpect(status().isForbidden());
            mvc.perform(json(put("/api/usuarios/5/password").with(a), PASSWORD)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void listar_admin_responde200ConPaginaYSinHash() throws Exception {
        when(servicio.listar(any(), any(), any(), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(respuesta()), i.getArgument(3), 1));

        mvc.perform(get("/api/usuarios?q=ana&rol=VENDEDOR&activo=true").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("ana@livorno.co"))
                .andExpect(jsonPath("$.content[0].debeCambiarPassword").value(true))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(20));
        verify(servicio).listar(eq("ana"), eq(Rol.VENDEDOR), eq(true), any(Pageable.class));
    }

    @Test
    void listar_rolInvalido_responde400() throws Exception {
        mvc.perform(get("/api/usuarios?rol=NADA").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtener_inexistente_responde404() throws Exception {
        when(servicio.obtener(9L)).thenThrow(new RecursoNoEncontradoException("Usuario", 9L));

        mvc.perform(get("/api/usuarios/9").with(authentication(como("ADMIN")))).andExpect(status().isNotFound());
    }

    @Test
    void crear_valido_responde201() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta());

        mvc.perform(json(post("/api/usuarios").with(authentication(como("ADMIN"))), CREAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void crear_passwordCorta_responde400ConErrorPorCampo() throws Exception {
        String cuerpo = """
                {"nombre":"Ana","email":"ana@livorno.co","rol":"VENDEDOR","passwordTemporal":"corta"}""";

        mvc.perform(json(post("/api/usuarios").with(authentication(como("ADMIN"))), cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("passwordTemporal"));
        verifyNoInteractions(servicio);
    }

    @Test
    void crear_correoInvalidoYRolNulo_responde400() throws Exception {
        String cuerpo = """
                {"nombre":"Ana","email":"no-es-correo","passwordTemporal":"Temporal-12345"}""";

        mvc.perform(json(post("/api/usuarios").with(authentication(como("ADMIN"))), cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2));
    }

    @Test
    void crear_correoDuplicado_responde409() throws Exception {
        when(servicio.crear(any())).thenThrow(new ConflictoException("Ya existe un usuario con ese correo."));

        mvc.perform(json(post("/api/usuarios").with(authentication(como("ADMIN"))), CREAR))
                .andExpect(status().isConflict());
    }

    @Test
    void actualizar_propioRol_responde422() throws Exception {
        when(servicio.actualizar(eq(1L), any())).thenThrow(new ExcepcionNegocio("No puedes cambiar tu propio rol."));

        mvc.perform(json(put("/api/usuarios/1").with(authentication(como("ADMIN"))), ACTUALIZAR))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("No puedes cambiar tu propio rol."));
    }

    @Test
    void cambiarEstado_ultimoAdmin_responde422() throws Exception {
        when(servicio.cambiarEstado(2L, false))
                .thenThrow(new ExcepcionNegocio("Debe quedar al menos un administrador activo."));

        mvc.perform(json(patch("/api/usuarios/2/estado").with(authentication(como("ADMIN"))), ESTADO))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarEstado_sinCampoActivo_responde400() throws Exception {
        mvc.perform(json(patch("/api/usuarios/2/estado").with(authentication(como("ADMIN"))), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restablecerPassword_valido_responde204() throws Exception {
        mvc.perform(json(put("/api/usuarios/5/password").with(authentication(como("ADMIN"))), PASSWORD))
                .andExpect(status().isNoContent());
        verify(servicio).restablecerPassword(eq(5L), any());
    }

    @Test
    void restablecerPassword_propioUsuario_responde422() throws Exception {
        org.mockito.Mockito.doThrow(new ExcepcionNegocio("Usa Mi cuenta para cambiar tu contraseña."))
                .when(servicio).restablecerPassword(eq(1L), any());

        mvc.perform(json(put("/api/usuarios/1/password").with(authentication(como("ADMIN"))), PASSWORD))
                .andExpect(status().isUnprocessableEntity());
    }
}
