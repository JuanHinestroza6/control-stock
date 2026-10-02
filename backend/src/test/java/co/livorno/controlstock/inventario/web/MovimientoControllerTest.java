package co.livorno.controlstock.inventario.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.KardexService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(MovimientoController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class MovimientoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean KardexService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void listar_contabilidad_devuelve200ConFiltros() throws Exception {
        var m = new MovimientoResponse(1L, LocalDateTime.parse("2026-10-02T10:00:00"), TipoMovimiento.ENTRADA, 5, 0, 5,
                0, 0, new BigDecimal("1000.00"), 10L, "LIV001-M-NEG", "Camiseta", null, null, null, null);
        when(servicio.consultar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(m)));

        mvc.perform(get("/api/movimientos").param("varianteId", "10").param("tipo", "ENTRADA")
                        .param("desde", "2026-10-01").param("hasta", "2026-10-02")
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.content[0].usuarioNombre").doesNotExist());
        verify(servicio).consultar(eq(10L), eq(TipoMovimiento.ENTRADA), eq(LocalDate.of(2026, 10, 1)),
                eq(LocalDate.of(2026, 10, 2)), any(Pageable.class));
    }

    @Test
    void listar_vendedorOSinAutenticar_rechaza() throws Exception {
        mvc.perform(get("/api/movimientos").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/movimientos")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_paramsInvalidosOFechasInvertidas_devuelve400() throws Exception {
        mvc.perform(get("/api/movimientos").param("tipo", "RARO").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/movimientos").param("desde", "ayer").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        when(servicio.consultar(any(), any(), any(), any(), any(Pageable.class)))
                .thenThrow(new SolicitudInvalidaException("La fecha inicial no puede ser posterior a la final."));
        mvc.perform(get("/api/movimientos").param("desde", "2026-10-03").param("hasta", "2026-10-02")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isBadRequest());
    }
}
