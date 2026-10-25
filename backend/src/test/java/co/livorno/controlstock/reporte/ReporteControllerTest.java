package co.livorno.controlstock.reporte;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.exportacion.ArchivoExportado;
import co.livorno.controlstock.comun.exportacion.ExportadorDocumentos;
import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.FiltroLibroVentas;
import co.livorno.controlstock.reporte.dto.FiltroMovimientos;
import co.livorno.controlstock.reporte.dto.MovimientosResponse;
import co.livorno.controlstock.reporte.dto.FiltroRentabilidad;
import co.livorno.controlstock.reporte.dto.FiltroValorizacion;
import co.livorno.controlstock.reporte.dto.PeriodoLibro;
import co.livorno.controlstock.reporte.dto.RentabilidadResponse;
import co.livorno.controlstock.reporte.dto.ValorizacionResponse;
import co.livorno.controlstock.reporte.servicio.LibroVentasService;
import co.livorno.controlstock.reporte.servicio.MovimientosPeriodoService;
import co.livorno.controlstock.reporte.servicio.RentabilidadService;
import co.livorno.controlstock.reporte.servicio.ValorizacionService;
import co.livorno.controlstock.reporte.web.ReporteController;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** B6, fase 7: seguridad por rol, encabezados de archivo y errores de los endpoints de reportes. */
@WebMvcTest(ReporteController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class ReporteControllerTest {

    private static final String[] RUTAS = {"/api/reportes/rentabilidad", "/api/reportes/libro-ventas",
        "/api/reportes/valorizacion", "/api/reportes/movimientos"};

    @Autowired MockMvc mvc;
    @MockitoBean RentabilidadService rentabilidad;
    @MockitoBean LibroVentasService libro;
    @MockitoBean ValorizacionService valorizacion;
    @MockitoBean MovimientosPeriodoService movimientos;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "María Pérez", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static RentabilidadResponse respuestaRentabilidad() {
        return new RentabilidadResponse(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), null, null,
                AgrupacionReporte.PRODUCTO, null, true, "No hay ventas en el periodo", List.of(), null, List.of(),
                LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    private static ValorizacionResponse respuestaValorizacion() {
        return new ValorizacionResponse(null, null, AgrupacionReporte.PRODUCTO, true, true, "Sin existencias",
                List.of(), null, List.of(), LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    private static MovimientosResponse respuestaMovimientos() {
        return new MovimientosResponse(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), null, null,
                AgrupacionReporte.PRODUCTO, true, "No hay movimientos en el periodo", List.of(), null, 0, List.of(),
                LocalDateTime.of(2026, 10, 14, 9, 0));
    }

    private void simularServicios() {
        when(rentabilidad.consultar(any())).thenReturn(respuestaRentabilidad());
        when(libro.consultar(any())).thenReturn(null);
        when(valorizacion.consultar(any())).thenReturn(respuestaValorizacion());
        var pdf = new ArchivoExportado("%PDF".getBytes(StandardCharsets.UTF_8), ExportadorDocumentos.TIPO_PDF,
                "reporte_ñandú.pdf");
        when(rentabilidad.exportar(any(), any(), any())).thenReturn(pdf);
        when(libro.exportar(any(), any(), any())).thenReturn(pdf);
        when(valorizacion.exportar(any(), any(), any())).thenReturn(pdf);
        when(movimientos.consultar(any())).thenReturn(respuestaMovimientos());
        when(movimientos.exportar(any(), any(), any())).thenReturn(pdf);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CONTABILIDAD"})
    void consultas_rolesAutorizados_devuelven200(String rol) throws Exception {
        simularServicios();

        mvc.perform(get("/api/reportes/rentabilidad").with(authentication(como(rol))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinVentas").value(true));
        mvc.perform(get("/api/reportes/libro-ventas").with(authentication(como(rol))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reportes/valorizacion").with(authentication(como(rol))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinExistencias").value(true));
        mvc.perform(get("/api/reportes/movimientos").with(authentication(como(rol))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinMovimientos").value(true))
                .andExpect(jsonPath("$.mensaje").value("No hay movimientos en el periodo"));
    }

    @Test
    void movimientos_armaElFiltroYExportaConElUsuarioActual() throws Exception {
        simularServicios();

        mvc.perform(get("/api/reportes/movimientos").param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .param("categoriaId", "3").param("agrupacion", "VARIANTE").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(movimientos).consultar(eq(new FiltroMovimientos(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                3L, AgrupacionReporte.VARIANTE)));

        mvc.perform(get("/api/reportes/movimientos/exportar").param("formato", "XLSX")
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk());
        verify(movimientos).exportar(eq(new FiltroMovimientos(null, null, null, null)), eq(FormatoExportacion.XLSX),
                eq("María Pérez"));
    }

    @Test
    void exportarMovimientos_detalleSobreElLimite_devuelve422() throws Exception {
        when(movimientos.exportar(any(), eq(FormatoExportacion.XLSX), any()))
                .thenThrow(new ExcepcionNegocio("El reporte tiene 60000 movimientos. Acote el rango."));

        mvc.perform(get("/api/reportes/movimientos/exportar").param("formato", "XLSX")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Acote el rango")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CONTABILIDAD"})
    void exportar_rolesAutorizados_devuelveArchivoConEncabezados(String rol) throws Exception {
        simularServicios();

        for (String ruta : RUTAS) {
            mvc.perform(get(ruta + "/exportar").param("formato", "PDF").with(authentication(como(rol))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Content-Disposition",
                            org.hamcrest.Matchers.startsWith("attachment; filename=\"reporte_")))
                    .andExpect(content().bytes("%PDF".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    void exportar_nombreConTildes_seCodificaEnContentDisposition() throws Exception {
        simularServicios();

        mvc.perform(get("/api/reportes/rentabilidad/exportar").param("formato", "XLSX")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("%C3%B1and%C3%BA")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVENTARIO", "VENDEDOR"})
    void reportes_rolesSinPermiso_devuelven403(String rol) throws Exception {
        simularServicios();

        for (String ruta : RUTAS) {
            mvc.perform(get(ruta).with(authentication(como(rol)))).andExpect(status().isForbidden());
            mvc.perform(get(ruta + "/exportar").param("formato", "PDF").with(authentication(como(rol))))
                    .andExpect(status().isForbidden());
        }
        verify(rentabilidad, never()).exportar(any(), any(), any());
    }

    @Test
    void reportes_sinToken_devuelven401() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta + "/exportar").param("formato", "PDF")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void exportar_formatoInvalido_devuelve400ProblemDetail() throws Exception {
        for (String ruta : RUTAS) {
            mvc.perform(get(ruta + "/exportar").param("formato", "DOC").with(authentication(como("ADMIN"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Test
    void exportar_sinFormato_devuelve400ProblemDetail() throws Exception {
        mvc.perform(get("/api/reportes/libro-ventas/exportar").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exportar_filtrosInvalidos_devuelve400ProblemDetailSinArchivo() throws Exception {
        when(rentabilidad.exportar(any(), any(), any()))
                .thenThrow(new SolicitudInvalidaException("desde", "La fecha inicial no puede ser posterior a la final."));

        mvc.perform(get("/api/reportes/rentabilidad/exportar").param("formato", "PDF")
                        .param("desde", "2026-10-10").param("hasta", "2026-10-01")
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.errores[0].campo").value("desde"));
    }

    @Test
    void exportar_fechaMalFormada_devuelve400() throws Exception {
        mvc.perform(get("/api/reportes/libro-ventas/exportar").param("formato", "XLSX").param("desde", "ayer")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void exportarLibro_detalleSobreElLimite_devuelve422() throws Exception {
        when(libro.exportar(any(), eq(FormatoExportacion.XLSX), any()))
                .thenThrow(new ExcepcionNegocio("El libro tiene 60000 líneas de venta. Acote el rango."));

        mvc.perform(get("/api/reportes/libro-ventas/exportar").param("formato", "XLSX")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Acote el rango")));
    }

    @Test
    void controller_armaFiltrosYUsaElNombreDelUsuarioActual() throws Exception {
        simularServicios();

        mvc.perform(get("/api/reportes/rentabilidad/exportar").param("formato", "PDF")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30").param("categoriaId", "4")
                        .param("agrupacion", "CATEGORIA").param("orden", "INGRESO")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(rentabilidad).exportar(
                eq(new FiltroRentabilidad(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 4L,
                        AgrupacionReporte.CATEGORIA, co.livorno.controlstock.reporte.dto.CriterioRentabilidad.INGRESO)),
                eq(FormatoExportacion.PDF), eq("María Pérez"));

        mvc.perform(get("/api/reportes/libro-ventas/exportar").param("formato", "XLSX").param("agrupacion", "MES")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(libro).exportar(eq(new FiltroLibroVentas(null, null, PeriodoLibro.MES)), eq(FormatoExportacion.XLSX),
                eq("María Pérez"));
    }

    @Test
    void valorizacion_sinIncluirInactivas_usaTruePorDefecto() throws Exception {
        simularServicios();

        mvc.perform(get("/api/reportes/valorizacion").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(valorizacion).consultar(eq(new FiltroValorizacion(null, null, true)));

        mvc.perform(get("/api/reportes/valorizacion").param("incluirInactivas", "false")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk());
        verify(valorizacion).consultar(eq(new FiltroValorizacion(null, null, false)));
    }
}
