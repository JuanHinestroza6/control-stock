package co.livorno.controlstock.reporte;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.reporte.dto.PanelResponse;
import co.livorno.controlstock.reporte.dto.ResumenReservas;
import co.livorno.controlstock.reporte.servicio.PanelService;
import co.livorno.controlstock.reporte.web.PanelController;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** B8, fase 7: el panel exige autenticación y el rol que decide las secciones sale del usuario autenticado. */
@WebMvcTest(PanelController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class PanelControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PanelService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD"})
    void panel_cuatroRoles_devuelve200YPasaElRolAlServicio(String rol) throws Exception {
        when(servicio.obtener(any())).thenReturn(new PanelResponse(LocalDateTime.of(2026, 10, 14, 9, 0), null, null,
                new ResumenReservas(2, 5, 1), null));

        mvc.perform(get("/api/panel").with(authentication(como(rol))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas.activas").value(2))
                .andExpect(jsonPath("$.reservas.porVencer24h").value(1));

        verify(servicio).obtener(rol);
    }

    @Test
    void panel_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/panel")).andExpect(status().isUnauthorized());

        verifyNoInteractions(servicio);
    }
}
