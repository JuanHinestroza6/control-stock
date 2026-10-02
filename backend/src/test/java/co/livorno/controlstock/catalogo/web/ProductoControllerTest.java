package co.livorno.controlstock.catalogo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.dto.CategoriaRef;
import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.dto.ProductoResumenResponse;
import co.livorno.controlstock.catalogo.servicio.ProductoService;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ManejadorGlobalExcepciones;
import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(ProductoController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionWeb.class})
class ProductoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductoService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static final String CREAR = """
            {"codigo":"LIV001","nombre":"Camiseta","categoriaId":2,"precioCompra":20000,"precioVenta":59900}""";
    private static final String ACTUALIZAR = """
            {"nombre":"Camiseta","categoriaId":2,"precioCompra":20000,"precioVenta":59900,"leadTimeDias":15,"loteMinimo":1}""";
    private static final String ESTADO = "{\"activo\":false}";

    private static ProductoDetalleResponse detalle() {
        return new ProductoDetalleResponse(10L, "LIV001", "Camiseta", new CategoriaRef(2L, "Camisetas"), null,
                new BigDecimal("59900.00"), 15, 1, true, 0, LocalDateTime.parse("2026-10-01T10:15:30"),
                LocalDateTime.parse("2026-10-01T10:15:30"));
    }

    @Test
    void listar_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_cualquierRol_responde200ConFormaDePagina() throws Exception {
        var resumen = new ProductoResumenResponse(10L, "LIV001", "Camiseta", new CategoriaRef(2L, "Camisetas"),
                new BigDecimal("59900.00"), true, 0, 0);
        when(servicio.listar(any(), any(), any(), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(resumen), i.getArgument(3), 1));

        for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            mvc.perform(get("/api/productos").with(authentication(como(rol))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].codigo").value("LIV001"))
                    .andExpect(jsonPath("$.content[0].precioVenta").value(59900.00))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Test
    void listar_ordenInvalido_responde400() throws Exception {
        when(servicio.listar(any(), any(), any(), any(Pageable.class)))
                .thenThrow(new SolicitudInvalidaException("Campo de orden no permitido: x"));

        mvc.perform(get("/api/productos").param("sort", "x,asc").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_busquedaMuyLarga_responde400() throws Exception {
        mvc.perform(get("/api/productos").param("q", "a".repeat(61)).with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_tamanoMayorA100_seLimitaA100() throws Exception {
        when(servicio.listar(any(), any(), any(), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<ProductoResumenResponse>(List.of(), i.getArgument(3), 0));

        mvc.perform(get("/api/productos").param("size", "500").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void obtener_cualquierRol_responde200() throws Exception {
        when(servicio.obtener(10L)).thenReturn(detalle());

        mvc.perform(get("/api/productos/10").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioCompra").doesNotExist())
                .andExpect(jsonPath("$.categoria.nombre").value("Camisetas"))
                .andExpect(jsonPath("$.creadoEn").value("2026-10-01T10:15:30"));
    }

    @Test
    void escritura_vendedorOContabilidad_responde403() throws Exception {
        for (String rol : List.of("VENDEDOR", "CONTABILIDAD")) {
            var auth = authentication(como(rol));
            mvc.perform(post("/api/productos").with(auth).contentType(MediaType.APPLICATION_JSON).content(CREAR))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/productos/10").with(auth).contentType(MediaType.APPLICATION_JSON).content(ACTUALIZAR))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/productos/10/estado").with(auth).contentType(MediaType.APPLICATION_JSON)
                    .content(ESTADO)).andExpect(status().isForbidden());
        }
    }

    @Test
    void crear_inventario_responde201() throws Exception {
        when(servicio.crear(any(CrearProductoRequest.class))).thenReturn(detalle());

        mvc.perform(post("/api/productos").with(authentication(como("INVENTARIO")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREAR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void crear_cuerpoInvalido_responde400ConErrores() throws Exception {
        String malo = """
                {"codigo":"a b","nombre":"","precioCompra":-1,"precioVenta":0,"leadTimeDias":400}""";

        mvc.perform(post("/api/productos").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo=='codigo')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='categoriaId')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='precioCompra')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='precioVenta')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='leadTimeDias')]").exists());
    }

    @Test
    void crear_codigoDuplicado_responde409() throws Exception {
        when(servicio.crear(any(CrearProductoRequest.class))).thenThrow(new ConflictoException("Ya existe"));

        mvc.perform(post("/api/productos").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREAR))
                .andExpect(status().isConflict());
    }

    @Test
    void actualizar_sinLeadTime_responde400() throws Exception {
        String sinLead = """
                {"nombre":"x","categoriaId":2,"precioCompra":1,"precioVenta":2}""";

        mvc.perform(put("/api/productos/10").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(sinLead))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo=='leadTimeDias')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='loteMinimo')]").exists());
    }

    @Test
    void cambiarEstado_admin_responde200() throws Exception {
        when(servicio.cambiarEstado(eq(10L), eq(false))).thenReturn(detalle());

        mvc.perform(patch("/api/productos/10/estado").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(ESTADO))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarEstado_sinCampo_responde400() throws Exception {
        mvc.perform(patch("/api/productos/10/estado").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
