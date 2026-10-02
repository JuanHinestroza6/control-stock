package co.livorno.controlstock.catalogo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse.Motivo;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse.Omitida;
import co.livorno.controlstock.catalogo.dto.VarianteResponse;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.servicio.VarianteService;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
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

@WebMvcTest(VarianteController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class VarianteControllerTest {

    private static final String GENERAR = """
            {"tallas":["M","L"],"colores":["Negro"],"stockMinimo":3}""";

    @Autowired MockMvc mvc;
    @MockitoBean VarianteService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static VarianteResponse variante() {
        return new VarianteResponse(1L, 10L, "LIV001-M-NEG", Talla.M, "NEGRO", 0, 0, 0, 3, null, null,
                EstadoStock.AGOTADO, true);
    }

    @Test
    void listar_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/productos/10/variantes")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_vendedor_responde200ConCampos() throws Exception {
        when(servicio.listar(10L, false)).thenReturn(List.of(variante()));

        mvc.perform(get("/api/productos/10/variantes").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("LIV001-M-NEG"))
                .andExpect(jsonPath("$[0].estadoStock").value("AGOTADO"))
                .andExpect(jsonPath("$[0].puntoReorden").doesNotExist());
    }

    @Test
    void listar_incluirInactivas_sePasaAlServicio() throws Exception {
        when(servicio.listar(10L, true)).thenReturn(List.of());

        mvc.perform(get("/api/productos/10/variantes?incluirInactivas=true")
                .with(authentication(como("CONTABILIDAD")))).andExpect(status().isOk());
    }

    @Test
    void generar_adminEInventario_responde201ConCreadasYOmitidas() throws Exception {
        when(servicio.generar(eq(10L), any())).thenReturn(new GenerarVariantesResponse(List.of(variante()),
                List.of(new Omitida(Talla.L, "NEGRO", "LIV001-L-NEG", Motivo.EXISTE_INACTIVA))));

        for (String rol : List.of("ADMIN", "INVENTARIO")) {
            mvc.perform(post("/api/productos/10/variantes/generar").with(authentication(como(rol)))
                    .contentType(MediaType.APPLICATION_JSON).content(GENERAR))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.creadas[0].sku").value("LIV001-M-NEG"))
                    .andExpect(jsonPath("$.omitidas[0].motivo").value("EXISTE_INACTIVA"));
        }
    }

    @Test
    void generar_vendedorYContabilidad_responde403() throws Exception {
        for (String rol : List.of("VENDEDOR", "CONTABILIDAD")) {
            mvc.perform(post("/api/productos/10/variantes/generar").with(authentication(como(rol)))
                    .contentType(MediaType.APPLICATION_JSON).content(GENERAR)).andExpect(status().isForbidden());
        }
    }

    @Test
    void generar_cuerpoInvalido_responde400ConErrores() throws Exception {
        mvc.perform(post("/api/productos/10/variantes/generar").with(authentication(como("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tallas\":[],\"colores\":[\"Neg@ro\"],\"stockMinimo\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void matriz_cualquierAutenticado_responde200() throws Exception {
        when(servicio.matriz(10L, false)).thenReturn(new co.livorno.controlstock.catalogo.dto.MatrizResponse(10L,
                List.of(Talla.M), List.of("NEGRO"), List.of(new co.livorno.controlstock.catalogo.dto.MatrizResponse
                        .Celda(Talla.M, "NEGRO", 1L, "LIV001-M-NEG", 0, 0, 0, EstadoStock.AGOTADO, true))));

        mvc.perform(get("/api/productos/10/matriz").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tallas[0]").value("M"))
                .andExpect(jsonPath("$.celdas[0].varianteId").value(1));
        mvc.perform(get("/api/productos/10/matriz")).andExpect(status().isUnauthorized());
    }

    @Test
    void actualizarYCambiarEstado_adminOk_vendedorYContabilidad403() throws Exception {
        when(servicio.actualizar(eq(1L), any())).thenReturn(variante());
        when(servicio.cambiarEstado(1L, false)).thenReturn(variante());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/variantes/1")
                .with(authentication(como("INVENTARIO"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockMinimo\":5}")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/variantes/1/estado")
                .with(authentication(como("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isOk());
        for (String rol : List.of("VENDEDOR", "CONTABILIDAD")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/variantes/1")
                    .with(authentication(como(rol))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"stockMinimo\":5}")).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/variantes/1/estado").with(authentication(como(rol)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void actualizar_stockMinimoInvalido_responde400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/variantes/1")
                .with(authentication(como("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockMinimo\":-1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("stockMinimo"));
    }

    @Test
    void cambiarEstado_conflictoYReglaDeNegocio_responden409Y422() throws Exception {
        when(servicio.cambiarEstado(1L, false)).thenThrow(
                new co.livorno.controlstock.comun.excepcion.ConflictoException("reservas"));
        when(servicio.cambiarEstado(2L, true)).thenThrow(new ExcepcionNegocio("Activa primero el producto X."));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/variantes/1/estado")
                .with(authentication(como("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":false}")).andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/variantes/2/estado")
                .with(authentication(como("ADMIN"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":true}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void generar_reglaDeNegocio_responde422() throws Exception {
        when(servicio.generar(eq(10L), any())).thenThrow(new ExcepcionNegocio("producto inactivo"));

        mvc.perform(post("/api/productos/10/variantes/generar").with(authentication(como("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content(GENERAR))
                .andExpect(status().isUnprocessableEntity());
    }
}
