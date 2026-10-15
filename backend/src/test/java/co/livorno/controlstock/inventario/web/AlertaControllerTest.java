package co.livorno.controlstock.inventario.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.AlertaResponse;
import co.livorno.controlstock.inventario.dto.ResumenAlertasResponse;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlertaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class AlertaControllerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Autowired MockMvc mvc;
    @MockitoBean AlertaService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static AlertaResponse alerta(EstadoAlerta estado, String atendidaPor) {
        return new AlertaResponse(4L, TipoAlerta.AGOTADO, estado, 9L, "LIV001-M-NEG", "Camiseta", Talla.M, "NEGRO", 0,
                5, T, T, estado == EstadoAlerta.ATENDIDA ? T : null, atendidaPor);
    }

    @Test
    void listar_vendedor_devuelve200ConElContrato() throws Exception {
        when(servicio.listar(eq(EstadoAlerta.ACTIVA), eq(TipoAlerta.AGOTADO), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alerta(EstadoAlerta.ACTIVA, null))));

        mvc.perform(get("/api/alertas?estado=ACTIVA&tipo=AGOTADO").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LIV001-M-NEG"))
                .andExpect(jsonPath("$.content[0].umbral").value(5))
                .andExpect(jsonPath("$.content[0].atendidaPor").doesNotExist());
    }

    @Test
    void resumen_inventario_devuelve200() throws Exception {
        when(servicio.resumen()).thenReturn(new ResumenAlertasResponse(3, 1, 2, 0));

        mvc.perform(get("/api/alertas/resumen").with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activas").value(3))
                .andExpect(jsonPath("$.agotado").value(1))
                .andExpect(jsonPath("$.stockBajo").value(2));
    }

    @Test
    void listarYResumen_contabilidad_devuelve403() throws Exception {
        mvc.perform(get("/api/alertas").with(authentication(como("CONTABILIDAD")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/alertas/resumen").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_sinAutenticar_devuelve401() throws Exception {
        mvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
    }

    @Test
    void atender_inventario_devuelve200() throws Exception {
        when(servicio.atender(4L, 1L)).thenReturn(alerta(EstadoAlerta.ATENDIDA, "U"));

        mvc.perform(post("/api/alertas/4/atender").with(authentication(como("INVENTARIO"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.atendidaPor").value("U"));
        verify(servicio).atender(4L, 1L);
    }

    @Test
    void atender_vendedor_devuelve403() throws Exception {
        mvc.perform(post("/api/alertas/4/atender").with(authentication(como("VENDEDOR"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void atender_yaAtendida_devuelve409() throws Exception {
        when(servicio.atender(4L, 1L)).thenThrow(new ConflictoException("La alerta ya fue atendida."));

        mvc.perform(post("/api/alertas/4/atender").with(authentication(como("ADMIN"))).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La alerta ya fue atendida."));
    }
}
