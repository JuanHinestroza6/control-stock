package co.livorno.controlstock.analitica.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.MasVendidoItem;
import co.livorno.controlstock.analitica.dto.MasVendidosResponse;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(AnaliticaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class AnaliticaControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MasVendidosService servicio;
    @MockitoBean co.livorno.controlstock.analitica.servicio.ConsultaAnaliticaService consulta;
    @MockitoBean co.livorno.controlstock.analitica.servicio.RecalculoAnaliticoService recalculo;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static MasVendidosResponse respuesta() {
        var item = new MasVendidoItem(1, 7L, "LIV001", "Camiseta", new CategoriaRef(2L, "Camisetas"), null, null, null,
                null, 12, new BigDecimal("1200.00"), new BigDecimal("40.0"), true);
        return new MasVendidosResponse(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14), CriterioRanking.UNIDADES,
                AgrupacionRanking.PRODUCTO, null, 30, new BigDecimal("3000.00"), List.of(item));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD"})
    void masVendidos_cuatroRoles_devuelve200ConElContrato(String rol) throws Exception {
        when(servicio.consultar(any(), any(), any(), any(), any(), any())).thenReturn(respuesta());

        mvc.perform(get("/api/analitica/mas-vendidos").with(authentication(como(rol))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnidades").value(30))
                .andExpect(jsonPath("$.items[0].productoCodigo").value("LIV001"))
                .andExpect(jsonPath("$.items[0].categoria.nombre").value("Camisetas"))
                .andExpect(jsonPath("$.items[0].participacion").value(40.0))
                .andExpect(jsonPath("$.items[0].costo").doesNotExist());
    }

    @Test
    void masVendidos_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/analitica/mas-vendidos")).andExpect(status().isUnauthorized());
    }

    @Test
    void masVendidos_sinParametros_delegaConNulosParaQueElServicioAplique​LosDefectos() throws Exception {
        when(servicio.consultar(any(), any(), any(), any(), any(), any())).thenReturn(respuesta());

        mvc.perform(get("/api/analitica/mas-vendidos").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk());

        verify(servicio).consultar(eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void masVendidos_parametrosCompletos_losPasaAlServicio() throws Exception {
        when(servicio.consultar(any(), any(), any(), any(), any(), any())).thenReturn(respuesta());

        mvc.perform(get("/api/analitica/mas-vendidos?desde=2026-01-01&hasta=2026-01-31&categoriaId=3"
                + "&criterio=INGRESO&agrupacion=VARIANTE&limite=5").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());

        verify(servicio).consultar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 3L, CriterioRanking.INGRESO,
                AgrupacionRanking.VARIANTE, 5);
    }

    @Test
    void masVendidos_servicioRechazaElRango_devuelve400ConElCampo() throws Exception {
        when(servicio.consultar(any(), any(), any(), any(), any(), any())).thenThrow(
                new SolicitudInvalidaException("desde", "La fecha inicial no puede ser posterior a la final."));

        mvc.perform(get("/api/analitica/mas-vendidos?desde=2026-02-01&hasta=2026-01-01")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    @Test
    void masVendidos_criterioInvalido_devuelve400ConElCampo() throws Exception {
        mvc.perform(get("/api/analitica/mas-vendidos?criterio=MARGEN").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("criterio"));
    }

    @Test
    void masVendidos_agrupacionYFechaInvalidas_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/mas-vendidos?agrupacion=MARCA").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("agrupacion"));
        mvc.perform(get("/api/analitica/mas-vendidos?desde=ayer").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }
}
