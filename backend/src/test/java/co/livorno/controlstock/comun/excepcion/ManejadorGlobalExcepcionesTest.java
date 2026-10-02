package co.livorno.controlstock.comun.excepcion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ManejadorGlobalExcepcionesTest.ControllerPrueba.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ManejadorGlobalExcepciones.class, ManejadorGlobalExcepcionesTest.ControllerPrueba.class})
class ManejadorGlobalExcepcionesTest {

    record Peticion(@NotBlank(message = "El nombre es obligatorio") String nombre) {}

    @RestController
    @RequestMapping("/prueba")
    static class ControllerPrueba {
        @PostMapping("/validar")
        String validar(@Valid @RequestBody Peticion p) { return "ok"; }

        @GetMapping("/parametro")
        String parametro(@RequestParam @Min(value = 1, message = "Debe ser mayor a 0") int cantidad) { return "ok"; }

        @GetMapping("/404")
        String noEncontrado() { throw new RecursoNoEncontradoException("Variante", 99); }

        @GetMapping("/409")
        String conflicto() { throw new ConflictoException("Ya existe"); }

        @GetMapping("/422")
        String negocio() { throw new ExcepcionNegocio("Stock insuficiente"); }

        @GetMapping("/403")
        String prohibido() { throw new AccessDeniedException("secreto interno"); }

        @GetMapping("/bloqueo")
        String bloqueo() { throw new OptimisticLockingFailureException("version"); }

        @GetMapping("/integridad")
        String integridad() { throw new DataIntegrityViolationException("Duplicate entry xyz for key"); }

        @GetMapping("/400")
        String solicitud() { throw new SolicitudInvalidaException("Parámetro inválido"); }

        @GetMapping("/orden")
        String orden() {
            throw new org.springframework.data.core.PropertyReferenceException("x",
                    org.springframework.data.core.TypeInformation.of(String.class), java.util.List.of());
        }

        @GetMapping("/500")
        String error() { throw new IllegalStateException("detalle interno sensible"); }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void validar_cuerpoInvalido_responde400ConErrores() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre es obligatorio"));
    }

    @Test
    void validar_parametroInvalido_responde400ConErrores() throws Exception {
        mvc.perform(get("/prueba/parametro").param("cantidad", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cantidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Debe ser mayor a 0"));
    }

    @Test
    void validar_jsonMalFormado_responde400() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
    }

    @Test
    void rutaInexistente_responde404EnEspanol() throws Exception {
        mvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("La ruta solicitada no existe."));
    }

    @Test
    void metodoNoPermitido_responde405EnEspanol() throws Exception {
        mvc.perform(post("/prueba/404"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    @Test
    void tipoDeContenidoNoSoportado_responde415EnEspanol() throws Exception {
        mvc.perform(post("/prueba/validar").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"));
    }

    @Test
    void solicitudInvalida_responde400() throws Exception {
        mvc.perform(get("/prueba/400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parámetro inválido"));
    }

    @Test
    void campoDeOrdenInexistente_responde400() throws Exception {
        mvc.perform(get("/prueba/orden"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Campo de orden no permitido"));
    }

    @Test
    void recursoNoEncontrado_responde404() throws Exception {
        mvc.perform(get("/prueba/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Variante con id 99 no existe"));
    }

    @Test
    void conflicto_responde409() throws Exception {
        mvc.perform(get("/prueba/409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe"));
    }

    @Test
    void bloqueoOptimista_responde409() throws Exception {
        mvc.perform(get("/prueba/bloqueo")).andExpect(status().isConflict());
    }

    @Test
    void integridadDatos_responde409SinExponerDetalleSql() throws Exception {
        mvc.perform(get("/prueba/integridad"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("Duplicate entry"))));
    }

    @Test
    void excepcionNegocio_responde422() throws Exception {
        mvc.perform(get("/prueba/422"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.detail").value("Stock insuficiente"));
    }

    @Test
    void accesoDenegado_responde403SinMensajeInterno() throws Exception {
        mvc.perform(get("/prueba/403"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("secreto interno"))));
    }

    @Test
    void errorGenerico_responde500SinStackTraceNiMensajeInterno() throws Exception {
        mvc.perform(get("/prueba/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(content().string(not(containsString("detalle interno sensible"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at co.livorno"))));
    }
}
