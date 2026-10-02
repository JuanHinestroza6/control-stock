package co.livorno.controlstock.venta.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.venta.dto.LineaVentaResponse;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.dto.VentaResumenResponse;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(VentaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class VentaControllerTest {

    static final String CUERPO = "{\"lineas\":[{\"varianteId\":5,\"cantidad\":2,\"precioUnitario\":50000.00}]}";

    @Autowired MockMvc mvc;
    @MockitoBean VentaService servicio;
    @MockitoBean co.livorno.controlstock.venta.servicio.DevolucionService devolucionService;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static VentaDetalleResponse detalle() {
        var linea = new LineaVentaResponse(1L, 5L, "LIV001-M-NEG", "Camiseta", Talla.M, "NEGRO", 2,
                new BigDecimal("59900.00"), new BigDecimal("50000.00"), null, new BigDecimal("100000.00"), 0,
                List.of());
        return new VentaDetalleResponse(42L, "V-000042", LocalDateTime.of(2026, 10, 2, 10, 0), "Ana",
                EstadoVenta.REGISTRADA, new BigDecimal("100000.00"), new BigDecimal("19800.00"), null, null, null,
                List.of(linea));
    }

    @Test
    void registrar_vendedor_devuelve201ConElContrato() throws Exception {
        when(servicio.registrar(any())).thenReturn(detalle());

        mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(authentication(como("VENDEDOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("V-000042"))
                .andExpect(jsonPath("$.descuentoTotal").value(19800.00))
                .andExpect(jsonPath("$.lineas[0].sku").value("LIV001-M-NEG"))
                .andExpect(jsonPath("$.lineas[0].costoUnitario").doesNotExist())
                .andExpect(jsonPath("$.lineas[0].devoluciones").isEmpty());
    }

    @Test
    void registrar_rolSinPermiso_devuelve403() throws Exception {
        for (String rol : List.of("INVENTARIO", "CONTABILIDAD")) {
            mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                            .with(authentication(como(rol))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void registrar_sinAutenticar_devuelve401() throws Exception {
        mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrar_cuerpoInvalido_devuelve400() throws Exception {
        for (String cuerpo : List.of("{\"lineas\":[]}",
                "{\"lineas\":[{\"varianteId\":5,\"cantidad\":0}]}",
                "{\"lineas\":[{\"cantidad\":1}]}",
                "{\"lineas\":[{\"varianteId\":5,\"cantidad\":1,\"precioUnitario\":0}]}")) {
            mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(authentication(como("ADMIN"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void registrar_masDe50Lineas_devuelve400() throws Exception {
        String linea = "{\"varianteId\":1,\"cantidad\":1}";
        String cuerpo = "{\"lineas\":[" + String.join(",", java.util.Collections.nCopies(51, linea)) + "]}";
        mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrar_reglaDeNegocioYVarianteRepetida_devuelve422Y400() throws Exception {
        when(servicio.registrar(any())).thenThrow(new ExcepcionNegocio("Stock insuficiente"));
        mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isUnprocessableContent());

        org.mockito.Mockito.doThrow(new SolicitudInvalidaException("repetida")).when(servicio).registrar(any());
        mvc.perform(post("/api/ventas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_contabilidadConFiltros_devuelve200YPasaParametros() throws Exception {
        var fila = new VentaResumenResponse(42L, "V-000042", LocalDateTime.of(2026, 10, 2, 10, 0), "Ana", 2,
                new BigDecimal("100000.00"), EstadoVenta.REGISTRADA);
        when(servicio.listar(any(), any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(fila)));

        mvc.perform(get("/api/ventas").param("desde", "2026-10-01").param("hasta", "2026-10-02")
                        .param("estado", "REGISTRADA").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value("V-000042"))
                .andExpect(jsonPath("$.content[0].unidades").value(2));

        var captor = ArgumentCaptor.forClass(Pageable.class);
        verify(servicio).listar(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 2)),
                eq(EstadoVenta.REGISTRADA), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().toString()).isEqualTo("fecha: DESC");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void listarYObtener_inventario_devuelve403() throws Exception {
        mvc.perform(get("/api/ventas").with(authentication(como("INVENTARIO")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas/1").with(authentication(como("INVENTARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anular_admin_devuelve200() throws Exception {
        when(servicio.anular(eq(42L), any())).thenReturn(detalle());

        mvc.perform(post("/api/ventas/42/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"error de captura\"}").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("V-000042"));
        verify(servicio).anular(eq(42L), eq(new co.livorno.controlstock.venta.dto.AnularVentaRequest("error de captura")));
    }

    @Test
    void anular_rolesNoAdmin_devuelve403() throws Exception {
        for (String rol : List.of("VENDEDOR", "INVENTARIO", "CONTABILIDAD")) {
            mvc.perform(post("/api/ventas/42/anular").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motivo\":\"error de captura\"}").with(authentication(como(rol))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void anular_motivoInvalido_devuelve400() throws Exception {
        for (String cuerpo : List.of("{}", "{\"motivo\":\"  \"}", "{\"motivo\":\"abc\"}")) {
            mvc.perform(post("/api/ventas/42/anular").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(authentication(como("ADMIN"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void anular_ventaYaAnuladaOConDevoluciones_devuelve409Y422() throws Exception {
        org.mockito.Mockito.doThrow(new co.livorno.controlstock.comun.excepcion.ConflictoException("ya anulada"))
                .when(servicio).anular(eq(42L), any());
        mvc.perform(post("/api/ventas/42/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"error de captura\"}").with(authentication(como("ADMIN"))))
                .andExpect(status().isConflict());

        org.mockito.Mockito.doThrow(new ExcepcionNegocio("tiene devoluciones")).when(servicio).anular(eq(43L), any());
        mvc.perform(post("/api/ventas/43/anular").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"error de captura\"}").with(authentication(como("ADMIN"))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void devolver_vendedor_devuelve201() throws Exception {
        when(devolucionService.registrar(eq(42L), any())).thenReturn(new co.livorno.controlstock.venta.dto.DevolucionResponse(
                9L, 1L, 1, "talla equivocada", true, LocalDateTime.of(2026, 10, 2, 11, 0), "Ana"));

        mvc.perform(post("/api/ventas/42/devoluciones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalleVentaId\":1,\"cantidad\":1,\"motivo\":\"talla equivocada\",\"aptaParaVenta\":true}")
                        .with(authentication(como("VENDEDOR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.usuarioNombre").value("Ana"));
    }

    @Test
    void devolver_rolSinPermiso_devuelve403() throws Exception {
        for (String rol : List.of("INVENTARIO", "CONTABILIDAD")) {
            mvc.perform(post("/api/ventas/42/devoluciones").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"detalleVentaId\":1,\"cantidad\":1,\"motivo\":\"talla equivocada\",\"aptaParaVenta\":true}")
                            .with(authentication(como(rol))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(devolucionService);
    }

    @Test
    void devolver_cuerpoInvalido_devuelve400() throws Exception {
        for (String cuerpo : List.of("{}",
                "{\"detalleVentaId\":1,\"cantidad\":0,\"motivo\":\"talla equivocada\",\"aptaParaVenta\":true}",
                "{\"detalleVentaId\":1,\"cantidad\":1,\"motivo\":\"abc\",\"aptaParaVenta\":true}",
                "{\"detalleVentaId\":1,\"cantidad\":1,\"motivo\":\"talla equivocada\"}")) {
            mvc.perform(post("/api/ventas/42/devoluciones").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(authentication(como("ADMIN"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void obtener_vendedor_devuelve200() throws Exception {
        when(servicio.obtener(42L)).thenReturn(detalle());

        mvc.perform(get("/api/ventas/42").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }
}
