package co.livorno.controlstock.inventario.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(InventarioOperacionesController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class InventarioOperacionesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OperacionInventarioService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static final String ENTRADA = """
            {"observacion":"Pedido","lineas":[{"varianteId":10,"cantidad":5,"costoUnitario":25000.00}]}""";
    private static final String AJUSTE = """
            {"varianteId":10,"tipo":"AJUSTE_NEGATIVO","cantidad":2,"observacion":"Merma por humedad"}""";

    private static MovimientoResponse movimiento(TipoMovimiento tipo) {
        return new MovimientoResponse(1L, LocalDateTime.parse("2026-10-02T10:00:00"), tipo, 5, 0, 5, 0, 0,
                new BigDecimal("25000.00"), 10L, "LIV001-M-NEG", "Camiseta", "U", null, null, "Pedido");
    }

    @Test
    void entradas_inventario_devuelve201() throws Exception {
        when(servicio.registrarEntrada(any())).thenReturn(List.of(movimiento(TipoMovimiento.ENTRADA)));

        mvc.perform(post("/api/inventario/entradas").contentType(MediaType.APPLICATION_JSON).content(ENTRADA)
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$[0].costoUnitario").value(25000.00));
    }

    @Test
    void entradas_vendedorOSinAutenticar_rechaza() throws Exception {
        mvc.perform(post("/api/inventario/entradas").contentType(MediaType.APPLICATION_JSON).content(ENTRADA)
                        .with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventario/entradas").contentType(MediaType.APPLICATION_JSON).content(ENTRADA))
                .andExpect(status().isUnauthorized());
        verify(servicio, never()).registrarEntrada(any());
    }

    @Test
    void entradas_cuerpoInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/inventario/entradas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineas\":[]}").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventario/entradas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineas\":[{\"varianteId\":1,\"cantidad\":0,\"costoUnitario\":-1}]}")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ajustes_admin_devuelve201() throws Exception {
        when(servicio.registrarAjuste(any())).thenReturn(movimiento(TipoMovimiento.AJUSTE_NEGATIVO));

        mvc.perform(post("/api/inventario/ajustes").contentType(MediaType.APPLICATION_JSON).content(AJUSTE)
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE_NEGATIVO"));
    }

    @Test
    void ajustes_observacionCortaOTipoDesconocido_devuelve400() throws Exception {
        mvc.perform(post("/api/inventario/ajustes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"varianteId\":10,\"tipo\":\"AJUSTE_NEGATIVO\",\"cantidad\":2,\"observacion\":\"ok\"}")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/inventario/ajustes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"varianteId\":10,\"tipo\":\"RARO\",\"cantidad\":2,\"observacion\":\"Merma larga\"}")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ajustes_contabilidad_devuelve403_yReglaDeNegocioDevuelve422() throws Exception {
        mvc.perform(post("/api/inventario/ajustes").contentType(MediaType.APPLICATION_JSON).content(AJUSTE)
                        .with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isForbidden());

        when(servicio.registrarAjuste(any())).thenThrow(new ExcepcionNegocio("Stock insuficiente para X: disponible 0, solicitado 2."));
        mvc.perform(post("/api/inventario/ajustes").contentType(MediaType.APPLICATION_JSON).content(AJUSTE)
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isUnprocessableEntity());
    }
}
