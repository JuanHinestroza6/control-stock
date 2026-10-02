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
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.InventarioItemResponse;
import co.livorno.controlstock.inventario.servicio.InventarioService;
import java.math.BigDecimal;
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

@WebMvcTest(InventarioController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class InventarioControllerConsultaTest {

    @Autowired MockMvc mvc;
    @MockitoBean InventarioService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void listar_sinAutenticar_devuelve401() throws Exception {
        mvc.perform(get("/api/inventario")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_vendedor_devuelve200ConElContratoSinCostos() throws Exception {
        // dado
        var item = new InventarioItemResponse(7L, "LIV001-M-NEG", 3L, "LIV001", "Camiseta",
                new CategoriaRef(2L, "Camisetas"), Talla.M, "NEGRO", 5, 3, 2, 2, null, EstadoStock.BAJO,
                new BigDecimal("59900.00"), true);
        when(servicio.listar(eq("liv"), eq(2L), eq(EstadoStock.BAJO), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        // cuando / entonces
        mvc.perform(get("/api/inventario").param("q", "liv").param("categoriaId", "2")
                        .param("estadoStock", "BAJO").param("incluirInactivas", "true")
                        .with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].varianteId").value(7))
                .andExpect(jsonPath("$.content[0].stockDisponible").value(2))
                .andExpect(jsonPath("$.content[0].estadoStock").value("BAJO"))
                .andExpect(jsonPath("$.content[0].categoria.nombre").value("Camisetas"))
                .andExpect(jsonPath("$.content[0].puntoReorden").doesNotExist())
                .andExpect(jsonPath("$.content[0].precioCompra").doesNotExist())
                .andExpect(jsonPath("$.content[0].costoUnitario").doesNotExist());
    }

    @Test
    void listar_sinParametros_usaDefectosYOrdenPorNombreYSku() throws Exception {
        when(servicio.listar(any(), any(), any(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/inventario").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(servicio).listar(any(), any(), any(), eq(false), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().toString())
                .isEqualTo("producto.nombre: ASC,sku: ASC");
    }

    @Test
    void listar_estadoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/api/inventario").param("estadoStock", "RARO")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }
}
