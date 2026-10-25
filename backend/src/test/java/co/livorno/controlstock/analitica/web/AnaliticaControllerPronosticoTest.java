package co.livorno.controlstock.analitica.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.analitica.dto.ParametrosModelo;
import co.livorno.controlstock.analitica.dto.PronosticoDetalleResponse;
import co.livorno.controlstock.analitica.dto.PronosticoProductoResponse;
import co.livorno.controlstock.analitica.dto.PronosticoVarianteResponse;
import co.livorno.controlstock.analitica.dto.PuntoPronostico;
import co.livorno.controlstock.analitica.dto.PuntoSerie;
import co.livorno.controlstock.analitica.dto.PuntoTendencia;
import co.livorno.controlstock.analitica.dto.TendenciaResponse;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.servicio.ConsultaAnaliticaService;
import co.livorno.controlstock.analitica.servicio.ConsultaPronosticoService;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import co.livorno.controlstock.analitica.servicio.RecalculoAnaliticoService;
import co.livorno.controlstock.analitica.servicio.TendenciasService;
import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** B10, fase 6: seguridad por rol y contrato de tendencias y pronósticos. */
@WebMvcTest(AnaliticaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class AnaliticaControllerPronosticoTest {

    private static final String[] RUTAS = {"/api/analitica/tendencias", "/api/analitica/pronosticos",
        "/api/analitica/pronosticos/7"};

    @Autowired MockMvc mvc;
    @MockitoBean MasVendidosService masVendidos;
    @MockitoBean ConsultaAnaliticaService consulta;
    @MockitoBean RecalculoAnaliticoService recalculo;
    @MockitoBean TendenciasService tendencias;
    @MockitoBean co.livorno.controlstock.analitica.servicio.ExportarTendenciasService exportarTendencias;
    @MockitoBean ConsultaPronosticoService pronosticos;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static PronosticoProductoResponse fila() {
        return new PronosticoProductoResponse(7L, "LIV001", "Camiseta", "Camisetas", MetodoPronostico.SES,
                new BigDecimal("0.1234"), new BigDecimal("0.1234"), null, null, 20, 120, new BigDecimal("131.50"),
                List.of(new PuntoPronostico("2026-10", new BigDecimal("10.50"))));
    }

    private void preparar() {
        when(tendencias.consultar(any(), any(), any(), any())).thenReturn(new TendenciaResponse("2026-08", "2026-09",
                null, null, List.of(new PuntoTendencia("2026-08", 10, new BigDecimal("1000.00"), 5,
                        new BigDecimal("500.00"), new BigDecimal("100.0"), new BigDecimal("100.0")),
                        new PuntoTendencia("2026-09", 0, new BigDecimal("0.00"), 0, new BigDecimal("0.00"), null,
                                null)),
                10, new BigDecimal("1000.00")));
        when(pronosticos.listar(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(fila())));
        var f = fila();
        when(pronosticos.detalle(7L)).thenReturn(new PronosticoDetalleResponse(f.productoId(), f.codigo(), f.nombre(),
                f.categoria(), f.metodo(), f.errorWape(), f.wapeSes(), f.wapeHw(), f.motivoSinHw(), f.mesesHistoria(),
                f.unidadesUltimos12m(), f.unidadesCorregidas12m(), f.proximos(),
                new ParametrosModelo(new BigDecimal("0.30"), null, null),
                List.of(new PuntoSerie("2026-09", 9, new BigDecimal("0.9000"), new BigDecimal("10.00"), null, null)),
                f.proximos(), List.of(new PronosticoVarianteResponse(70L, "LIV001-M-NEG", Talla.M, "NEGRO",
                        new BigDecimal("0.6000"), f.proximos())),
                LocalDateTime.of(2026, 10, 14, 2, 0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "CONTABILIDAD"})
    void lecturas_rolesPermitidos_devuelven200(String rol) throws Exception {
        preparar();
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).with(authentication(como(rol)))).andExpect(status().isOk());
        }
    }

    @Test
    void lecturas_vendedor_devuelve403() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).with(authentication(como("VENDEDOR")))).andExpect(status().isForbidden());
        }
    }

    @Test
    void lecturas_sinToken_devuelve401() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tendencias_devuelveElContratoYPasaLosFiltros() throws Exception {
        preparar();
        mvc.perform(get("/api/analitica/tendencias?desde=2026-08&hasta=2026-09&categoriaId=2&productoId=7")
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos[0].periodo").value("2026-08"))
                .andExpect(jsonPath("$.puntos[0].variacionUnidades").value(100.0))
                .andExpect(jsonPath("$.puntos[1].variacionIngreso").doesNotExist())
                .andExpect(jsonPath("$.totalIngreso").value(1000.0));
        verify(tendencias).consultar("2026-08", "2026-09", 2L, 7L);
    }

    @Test
    void tendencias_rangoInvalido_devuelve400() throws Exception {
        when(tendencias.consultar(any(), any(), any(), any()))
                .thenThrow(new SolicitudInvalidaException("desde", "El rango no puede superar 36 meses."));
        mvc.perform(get("/api/analitica/tendencias?desde=2020-01&hasta=2026-09").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    @Test
    void pronosticos_devuelveElContratoYPasaLosFiltros() throws Exception {
        preparar();
        mvc.perform(get("/api/analitica/pronosticos?categoriaId=2&q=liv&metodo=SES&page=0&size=5&sort=errorWape,desc")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("LIV001"))
                .andExpect(jsonPath("$.content[0].metodo").value("SES"))
                .andExpect(jsonPath("$.content[0].errorWape").value(0.1234))
                .andExpect(jsonPath("$.content[0].proximos[0].periodo").value("2026-10"))
                .andExpect(jsonPath("$.content[0].costoUnitario").doesNotExist());
        verify(pronosticos).listar(eq(2L), eq("liv"), eq(MetodoPronostico.SES), any(Pageable.class));
    }

    @Test
    void pronosticos_metodoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos?metodo=MAGICO").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("metodo"));
    }

    @Test
    void pronosticos_sinFiltros_pasaNulos() throws Exception {
        preparar();
        mvc.perform(get("/api/analitica/pronosticos").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(pronosticos).listar(isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void pronostico_detalle_devuelveSerieYVariantes() throws Exception {
        preparar();
        mvc.perform(get("/api/analitica/pronosticos/7").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parametros.alfa").value(0.30))
                .andExpect(jsonPath("$.serie[0].disponibilidad").value(0.9))
                .andExpect(jsonPath("$.variantes[0].sku").value("LIV001-M-NEG"))
                .andExpect(jsonPath("$.variantes[0].talla").value("M"))
                .andExpect(jsonPath("$.variantes[0].participacion").value(0.6))
                .andExpect(jsonPath("$.pronostico[0].cantidad").value(10.5));
    }

    @Test
    void pronostico_detalle_inexistente_devuelve404() throws Exception {
        when(pronosticos.detalle(99L)).thenThrow(new RecursoNoEncontradoException("Pronóstico del producto", 99L));
        mvc.perform(get("/api/analitica/pronosticos/99").with(authentication(como("ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
