package co.livorno.controlstock.catalogo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.auth.config.ConfiguracionSeguridad;
import co.livorno.controlstock.catalogo.dto.CategoriaRequest;
import co.livorno.controlstock.catalogo.dto.CategoriaResponse;
import co.livorno.controlstock.catalogo.servicio.CategoriaService;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
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

@WebMvcTest(CategoriaController.class)
@ActiveProfiles("test")
@Import({ConfiguracionSeguridad.class, ManejadorGlobalExcepciones.class, UsuarioActual.class,
        co.livorno.controlstock.comun.config.ConfiguracionTiempo.class})
class CategoriaControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CategoriaService servicio;
    @MockitoBean co.livorno.controlstock.usuario.repositorio.UsuarioRepository usuarioRepository;

    private static Authentication como(String rol) {
        var principal = new UsuarioAutenticado(1L, "u@livorno.test", "U", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private static final String CUERPO = "{\"nombre\":\"Gorras\"}";

    @Test
    void listar_sinToken_responde401() throws Exception {
        mvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_cualquierRol_responde200() throws Exception {
        when(servicio.listar()).thenReturn(List.of(new CategoriaResponse(1L, "Jeans", 0)));

        for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            mvc.perform(get("/api/categorias").with(authentication(como(rol))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nombre").value("Jeans"))
                    .andExpect(jsonPath("$[0].totalProductos").value(0));
        }
    }

    @Test
    void escritura_vendedorOContabilidad_responde403() throws Exception {
        for (String rol : List.of("VENDEDOR", "CONTABILIDAD")) {
            var auth = authentication(como(rol));
            mvc.perform(post("/api/categorias").with(auth).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/categorias/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/categorias/1").with(auth)).andExpect(status().isForbidden());
        }
    }

    @Test
    void crear_inventario_responde201() throws Exception {
        when(servicio.crear(any(CategoriaRequest.class))).thenReturn(new CategoriaResponse(7L, "Gorras", 0));

        mvc.perform(post("/api/categorias").with(authentication(como("INVENTARIO")))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void crear_nombreVacio_responde400ConErrores() throws Exception {
        mvc.perform(post("/api/categorias").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    @Test
    void crear_duplicada_responde409() throws Exception {
        when(servicio.crear(any(CategoriaRequest.class))).thenThrow(new ConflictoException("Ya existe"));

        mvc.perform(post("/api/categorias").with(authentication(como("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminar_admin_responde204() throws Exception {
        mvc.perform(delete("/api/categorias/1").with(authentication(como("ADMIN"))))
                .andExpect(status().isNoContent());
    }
}
