package co.livorno.controlstock.compra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.compra.dto.PlanCompraResumenResponse;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** B11, fase 6: matriz de roles completa y contrato HTTP de los planes de compra (ADR-010). */
@WebMvcTest(PlanCompraController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class PlanCompraControllerTest {

    private static final String BASE = "/api/compras/planes";
    private static final String LINEAS = "\"lineas\":[{\"varianteId\":1,\"cantidad\":5}]";

    /** Endpoint de la matriz con los roles que la matriz de dominio.md permite. */
    private enum Ep {
        LISTAR("ADMIN", "INVENTARIO", "CONTABILIDAD"),
        VER("ADMIN", "INVENTARIO", "CONTABILIDAD"),
        CREAR("ADMIN", "INVENTARIO"),
        SUGERIR("ADMIN", "INVENTARIO"),
        EDITAR("ADMIN", "INVENTARIO"),
        APROBAR("ADMIN"),
        CANCELAR("ADMIN", "INVENTARIO"),
        RECIBIR("ADMIN", "INVENTARIO");

        final List<String> permitidos;

        Ep(String... permitidos) {
            this.permitidos = List.of(permitidos);
        }

        MockHttpServletRequestBuilder peticion() {
            return switch (this) {
                case LISTAR -> get(BASE);
                case VER -> get(BASE + "/5");
                case CREAR -> json(post(BASE), "{" + LINEAS + "}");
                case SUGERIR -> json(post(BASE + "/sugerido"), "{}");
                case EDITAR -> json(put(BASE + "/5"), "{\"version\":0," + LINEAS + "}");
                case APROBAR -> json(post(BASE + "/5/aprobar"), "{\"version\":0}");
                case CANCELAR -> json(post(BASE + "/5/cancelar"), "{\"motivo\":\"Proveedor sin stock\"}");
                case RECIBIR -> json(post(BASE + "/5/recibir"),
                        "{\"lineas\":[{\"detalleId\":1,\"cantidadRecibida\":5,\"costoUnitario\":10}]}");
            };
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean PlanCompraService service;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String cuerpo) {
        return b.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private ResultActions ejecutar(Ep ep, String rol) throws Exception {
        var req = ep.peticion();
        return mvc.perform(rol == null ? req : req.with(authentication(como(rol))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD"})
    void matriz_cadaEndpointPorRol_respetaLosPermisos(String rol) throws Exception {
        // dado un servicio simulado que responde sin error
        when(service.listar(any(), any())).thenReturn(new PageImpl<PlanCompraResumenResponse>(List.of()));
        for (Ep ep : Ep.values()) {
            // cuando se llama con el rol
            int estado = ejecutar(ep, rol).andReturn().getResponse().getStatus();
            // entonces 403 si no está permitido; si lo está, pasa la seguridad
            if (ep.permitidos.contains(rol)) {
                assertThat(estado).as(ep + " como " + rol).isNotIn(401, 403);
            } else {
                assertThat(estado).as(ep + " como " + rol).isEqualTo(403);
            }
        }
    }

    @Test
    void matriz_sinToken_devuelve401EnTodosLosEndpoints() throws Exception {
        for (Ep ep : Ep.values()) {
            ejecutar(ep, null).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void cancelar_inventarioSobreAprobado_devuelve403DesdeElServicio() throws Exception {
        when(service.cancelar(eq(5L), any())).thenThrow(new AccessDeniedException("no"));

        ejecutar(Ep.CANCELAR, "INVENTARIO").andExpect(status().isForbidden());
    }

    @Test
    void listar_conParametros_pasaEstadoYPaginacion() throws Exception {
        when(service.listar(any(), any())).thenReturn(new PageImpl<PlanCompraResumenResponse>(List.of()));

        mvc.perform(get(BASE + "?estado=APROBADO&page=1&size=5&sort=creadoEn,desc")
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        verify(service).listar(eq(EstadoPlan.APROBADO), any(Pageable.class));
    }

    @Test
    void crear_ok_devuelve201() throws Exception {
        ejecutar(Ep.CREAR, "INVENTARIO").andExpect(status().isCreated());
    }

    @Test
    void crear_sinLineas_devuelve400() throws Exception {
        mvc.perform(json(post(BASE), "{\"lineas\":[]}").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aprobar_sinVersion_devuelve400() throws Exception {
        mvc.perform(json(post(BASE + "/5/aprobar"), "{}").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errores_delServicio_seMapeanA404409y422() throws Exception {
        when(service.obtener(7L)).thenThrow(new RecursoNoEncontradoException("Plan de compra", 7L));
        when(service.aprobar(eq(5L), any())).thenThrow(new ConflictoException("El plan PC-000005 ya está APROBADO."));
        when(service.recibir(eq(5L), any())).thenThrow(new ExcepcionNegocio("Todas las líneas en 0"));

        mvc.perform(get(BASE + "/7").with(authentication(como("ADMIN")))).andExpect(status().isNotFound());
        ejecutar(Ep.APROBAR, "ADMIN").andExpect(status().isConflict());
        ejecutar(Ep.RECIBIR, "ADMIN").andExpect(status().isUnprocessableContent());
    }
}
