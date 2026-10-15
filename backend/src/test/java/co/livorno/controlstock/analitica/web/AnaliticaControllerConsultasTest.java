package co.livorno.controlstock.analitica.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.analitica.dto.AbcVarianteResponse;
import co.livorno.controlstock.analitica.dto.ClaseResumen;
import co.livorno.controlstock.analitica.dto.EjecucionAnaliticaResponse;
import co.livorno.controlstock.analitica.dto.FuenteUmbral;
import co.livorno.controlstock.analitica.dto.ReordenVarianteResponse;
import co.livorno.controlstock.analitica.dto.ResumenAbcResponse;
import co.livorno.controlstock.analitica.modelo.ClaseAbc;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.servicio.ConsultaAnaliticaService;
import co.livorno.controlstock.analitica.servicio.EjecucionEnCursoException;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import co.livorno.controlstock.analitica.servicio.RecalculoAnaliticoService;
import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

/** B7, fase 5: seguridad por rol y contrato de los endpoints de ABC, reorden, ejecuciones y recálculo. */
@WebMvcTest(AnaliticaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class AnaliticaControllerConsultasTest {

    private static final String[] RUTAS_LECTURA = {"/api/analitica/abc", "/api/analitica/abc/resumen",
        "/api/analitica/reorden", "/api/analitica/ejecuciones/ultima"};

    @Autowired MockMvc mvc;
    @MockitoBean MasVendidosService masVendidos;
    @MockitoBean ConsultaAnaliticaService consulta;
    @MockitoBean RecalculoAnaliticoService recalculo;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static EjecucionAnaliticaResponse ejecucion() {
        return new EjecucionAnaliticaResponse(5L, OrigenEjecucion.MANUAL, "Ana", LocalDateTime.of(2026, 10, 14, 9, 0),
                LocalDateTime.of(2026, 10, 14, 9, 1), EstadoEjecucion.EXITOSA, LocalDate.of(2026, 10, 14), 1200L, 299,
                20, 60, 219, 150, 299, 0, 299, 141, new BigDecimal("47.2"), 3, null);
    }

    private void prepararLectura() {
        when(consulta.listarAbc(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new AbcVarianteResponse(7L, "LIV001-M-NEG", 3L, "Camiseta", new CategoriaRef(2L, "Camisetas"),
                        Talla.M, "NEGRO", 1, 40, new BigDecimal("4000.00"), new BigDecimal("55.50"),
                        new BigDecimal("55.50"), "A"))));
        when(consulta.resumenAbc()).thenReturn(new ResumenAbcResponse(ejecucion(), LocalDate.of(2025, 10, 14),
                LocalDate.of(2026, 10, 13), new BigDecimal("7200.00"),
                List.of(new ClaseResumen("A", 20, new BigDecimal("4000.00"), new BigDecimal("55.56")))));
        when(consulta.listarReorden(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                new ReordenVarianteResponse(7L, "LIV001-M-NEG", "Camiseta", new CategoriaRef(2L, "Camisetas"),
                        Talla.M, "NEGRO", "A", 90, new BigDecimal("1.5000"), new BigDecimal("0.8000"), 15,
                        new BigDecimal("0.950"), new BigDecimal("1.65"), new BigDecimal("5.10"), 28,
                        MetodoReorden.NORMAL, 2, 28, FuenteUmbral.ROP, 10, EstadoStock.BAJO))));
        when(consulta.ultimaEjecucion()).thenReturn(Optional.of(ejecucion()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "INVENTARIO", "CONTABILIDAD"})
    void lecturas_rolesPermitidos_devuelven200(String rol) throws Exception {
        prepararLectura();
        for (String ruta : RUTAS_LECTURA) {
            mvc.perform(get(ruta).with(authentication(como(rol)))).andExpect(status().isOk());
        }
    }

    @Test
    void lecturas_vendedor_devuelve403() throws Exception {
        for (String ruta : RUTAS_LECTURA) {
            mvc.perform(get(ruta).with(authentication(como("VENDEDOR")))).andExpect(status().isForbidden());
        }
    }

    @Test
    void lecturas_sinToken_devuelve401() throws Exception {
        for (String ruta : RUTAS_LECTURA) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void abc_devuelveElContratoYPasaLosFiltros() throws Exception {
        prepararLectura();
        mvc.perform(get("/api/analitica/abc?clase=A&categoriaId=2&q=liv&page=1&size=5&sort=ingresoNeto,desc")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("LIV001-M-NEG"))
                .andExpect(jsonPath("$.content[0].participacion").value(55.5))
                .andExpect(jsonPath("$.content[0].claseAbc").value("A"))
                .andExpect(jsonPath("$.content[0].categoria.nombre").value("Camisetas"));
        verify(consulta).listarAbc(eq(ClaseAbc.A), eq(2L), eq("liv"), any(Pageable.class));
    }

    @Test
    void abc_claseInvalida_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/abc?clase=Z").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("clase"));
    }

    @Test
    void abc_ordenNoPermitido_devuelve400() throws Exception {
        when(consulta.listarAbc(any(), any(), any(), any()))
                .thenThrow(new SolicitudInvalidaException("Campo de orden no permitido: costo"));
        mvc.perform(get("/api/analitica/abc?sort=costo").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumenAbc_devuelveClasesYVentana() throws Exception {
        prepararLectura();
        mvc.perform(get("/api/analitica/abc/resumen").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ventanaDesde").value("2025-10-14"))
                .andExpect(jsonPath("$.clases[0].clase").value("A"))
                .andExpect(jsonPath("$.clases[0].participacion").value(55.56));
    }

    @Test
    void reorden_devuelveElContratoYPasaLosFiltros() throws Exception {
        prepararLectura();
        mvc.perform(get("/api/analitica/reorden?clase=B&metodo=POISSON&enAlerta=true")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].umbralEfectivo").value(28))
                .andExpect(jsonPath("$.content[0].fuenteUmbral").value("ROP"))
                .andExpect(jsonPath("$.content[0].estadoStock").value("BAJO"))
                .andExpect(jsonPath("$.content[0].metodo").value("NORMAL"));
        verify(consulta).listarReorden(eq(ClaseAbc.B), isNull(), isNull(), eq(MetodoReorden.POISSON), eq(true),
                any(Pageable.class));
    }

    @Test
    void reorden_metodoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/reorden?metodo=MAGICO").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("metodo"));
    }

    @Test
    void ultimaEjecucion_sinEjecuciones_devuelve204() throws Exception {
        when(consulta.ultimaEjecucion()).thenReturn(Optional.empty());
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void ultimaEjecucion_conEjecucion_incluyeUsuarioYPorcentaje() throws Exception {
        prepararLectura();
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("Ana"))
                .andExpect(jsonPath("$.porcentajeAlertaStock").value(47.2))
                .andExpect(jsonPath("$.estado").value("EXITOSA"));
    }

    @Test
    void recalcular_admin_devuelve200ConLaEjecucion() throws Exception {
        when(recalculo.recalcular()).thenReturn(ejecucion());
        mvc.perform(post("/api/analitica/recalcular").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.claseA").value(20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVENTARIO", "CONTABILIDAD", "VENDEDOR"})
    void recalcular_otrosRoles_devuelve403(String rol) throws Exception {
        mvc.perform(post("/api/analitica/recalcular").with(authentication(como(rol))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recalculo);
    }

    @Test
    void recalcular_sinToken_devuelve401() throws Exception {
        mvc.perform(post("/api/analitica/recalcular")).andExpect(status().isUnauthorized());
    }

    @Test
    void recalcular_hayOtroEnCurso_devuelve409() throws Exception {
        when(recalculo.recalcular()).thenThrow(new EjecucionEnCursoException());
        mvc.perform(post("/api/analitica/recalcular").with(authentication(como("ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya hay un recálculo en curso."));
    }

    @Test
    void ultimaEjecucion_fallidaOEnCurso_serializaNulosYTodosLosCamposDelContrato() throws Exception {
        // dado: EN_CURSO sin conteos y FALLIDA solo con la metrica de alertas (contrato de tipos/analitica.ts)
        var enCurso = new EjecucionAnaliticaResponse(8L, OrigenEjecucion.NOCTURNA, null,
                LocalDateTime.of(2026, 10, 14, 2, 0), null, EstadoEjecucion.EN_CURSO, LocalDate.of(2026, 10, 14),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        var fallida = new EjecucionAnaliticaResponse(9L, OrigenEjecucion.MANUAL, "Ana",
                LocalDateTime.of(2026, 10, 14, 2, 0), LocalDateTime.of(2026, 10, 14, 2, 1), EstadoEjecucion.FALLIDA,
                LocalDate.of(2026, 10, 14), 500L, null, null, null, null, null, null, null, 299, 141,
                new BigDecimal("47.16"), 3, "Fallo el calculo");
        String[] campos = {"id", "origen", "usuario", "iniciadaEn", "terminadaEn", "estado", "fechaCorte", "duracionMs",
            "variantesAnalizadas", "claseA", "claseB", "claseC", "conPuntoReorden", "alertasEvaluadas",
            "alertasFallidas", "variantesActivas", "enAlertaStock", "porcentajeAlertaStock", "sinRotacionActivas",
            "mensaje"};

        // cuando / entonces: las 20 claves existen (null explicito), ni mas ni menos
        when(consulta.ultimaEjecucion()).thenReturn(Optional.of(enCurso));
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.length()").value(campos.length))
                .andExpect(jsonPath("$.terminadaEn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.variantesAnalizadas").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.porcentajeAlertaStock").value(org.hamcrest.Matchers.nullValue()));

        when(consulta.ultimaEjecucion()).thenReturn(Optional.of(fallida));
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FALLIDA"))
                .andExpect(jsonPath("$.claseA").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.enAlertaStock").value(141))
                .andExpect(jsonPath("$.porcentajeAlertaStock").value(47.16))
                .andExpect(jsonPath("$.mensaje").value("Fallo el calculo"));
    }

    @Test
    void resumenAbc_sinEjecucionExitosa_serializaEjecucionYVentanaNulas() throws Exception {
        when(consulta.resumenAbc()).thenReturn(new ResumenAbcResponse(null, null, null, new BigDecimal("0.00"),
                List.of(new ClaseResumen("A", 0, new BigDecimal("0.00"), new BigDecimal("0.00")))));
        mvc.perform(get("/api/analitica/abc/resumen").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejecucion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ventanaDesde").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ventanaHasta").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.totalIngreso").value(0.0));
    }
}
