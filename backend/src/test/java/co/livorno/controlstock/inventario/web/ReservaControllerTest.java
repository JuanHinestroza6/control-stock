package co.livorno.controlstock.inventario.web;

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
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
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

@WebMvcTest(ReservaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class ReservaControllerTest {

    static final String CUERPO = "{\"clienteNombre\":\"Ana Pérez\",\"clienteContacto\":\"3001234567\","
            + "\"lineas\":[{\"varianteId\":5,\"cantidad\":2}]}";
    static final List<String> ROLES_OK = List.of("ADMIN", "INVENTARIO", "VENDEDOR");

    @Autowired MockMvc mvc;
    @MockitoBean ReservaService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static ReservaResponse reserva() {
        return new ReservaResponse(12L, "R-000012", 5L, "LIV001-M-NEG", "Camiseta", Talla.M, "NEGRO", 2,
                new BigDecimal("59900.00"), "Ana Pérez", "3001234567", EstadoReserva.ACTIVA,
                LocalDateTime.of(2026, 10, 2, 10, 0), LocalDateTime.of(2026, 10, 4, 10, 0), "Ana", null, null, null,
                null, null);
    }

    @Test
    void crear_rolesPermitidos_devuelve201ConArregloYContrato() throws Exception {
        when(servicio.crear(any())).thenReturn(List.of(reserva()));

        for (String rol : ROLES_OK) {
            mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                            .with(authentication(como(rol))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].numero").value("R-000012"))
                    .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                    .andExpect(jsonPath("$[0].talla").value("M"))
                    .andExpect(jsonPath("$[0].cerradaPor").isEmpty());
        }
    }

    @Test
    void crear_contabilidad_devuelve403() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    void crear_sinAutenticar_devuelve401() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crear_cuerpoInvalido_devuelve400() throws Exception {
        String linea = "{\"varianteId\":1,\"cantidad\":1}";
        String veintiuna = String.join(",", Collections.nCopies(21, linea));
        for (String cuerpo : List.of(
                "{\"clienteNombre\":\"A\",\"clienteContacto\":\"300\",\"lineas\":[" + linea + "]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"30\",\"lineas\":[" + linea + "]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"300\",\"lineas\":[]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"300\",\"lineas\":[" + veintiuna + "]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"300\",\"lineas\":[{\"varianteId\":1,\"cantidad\":101}]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"300\",\"vigenciaHoras\":169,\"lineas\":[" + linea + "]}",
                "{\"clienteNombre\":\"Ana\",\"clienteContacto\":\"300\",\"vigenciaHoras\":0,\"lineas\":[" + linea + "]}")) {
            mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(authentication(como("ADMIN"))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void crear_stockInsuficiente_devuelve422() throws Exception {
        when(servicio.crear(any())).thenThrow(new ExcepcionNegocio("Stock insuficiente"));

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(authentication(como("VENDEDOR"))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void cancelar_ok409YMotivoInvalido() throws Exception {
        when(servicio.cancelar(eq(12L), any())).thenReturn(reserva());
        mvc.perform(post("/api/reservas/12/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistió\"}").with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/reservas/12/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"abc\"}").with(authentication(como("INVENTARIO"))))
                .andExpect(status().isBadRequest());

        when(servicio.cancelar(eq(13L), any())).thenThrow(new ConflictoException("ya está CONVERTIDA"));
        mvc.perform(post("/api/reservas/13/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistió\"}").with(authentication(como("ADMIN"))))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelar_contabilidad_devuelve403() throws Exception {
        mvc.perform(post("/api/reservas/12/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente desistió\"}").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void extender_okYValidaciones() throws Exception {
        when(servicio.extender(eq(12L), any())).thenReturn(reserva());
        mvc.perform(post("/api/reservas/12/extender").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horas\":24}").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk());

        for (String cuerpo : List.of("{\"horas\":0}", "{\"horas\":169}", "{}")) {
            mvc.perform(post("/api/reservas/12/extender").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                            .with(authentication(como("VENDEDOR"))))
                    .andExpect(status().isBadRequest());
        }

        when(servicio.extender(eq(14L), any())).thenThrow(new ExcepcionNegocio("más de 7 días"));
        mvc.perform(post("/api/reservas/14/extender").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horas\":24}").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void listar_pasaFiltrosYOrdenPorDefecto() throws Exception {
        when(servicio.listar(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(reserva())));

        mvc.perform(get("/api/reservas").param("estado", "ACTIVA").param("q", "ana").param("varianteId", "5")
                        .param("porVencer", "true").with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].numero").value("R-000012"));

        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(servicio).listar(eq(EstadoReserva.ACTIVA), eq("ana"), eq(5L), eq(true), p.capture());
        org.assertj.core.api.Assertions.assertThat(p.getValue().getPageSize()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(p.getValue().getSort().toString()).isEqualTo("venceEn: ASC");
    }

    @Test
    void listarYObtener_contabilidad_devuelve403() throws Exception {
        mvc.perform(get("/api/reservas").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reservas/12").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_existenteEInexistente() throws Exception {
        when(servicio.obtener(12L)).thenReturn(reserva());
        when(servicio.obtener(99L)).thenThrow(new RecursoNoEncontradoException("Reserva", 99L));

        mvc.perform(get("/api/reservas/12").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sku").value("LIV001-M-NEG"));
        mvc.perform(get("/api/reservas/99").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isNotFound());
    }
}
