package co.livorno.controlstock.comun.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(PaginaJsonTest.ControllerPagina.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ConfiguracionWeb.class, PaginaJsonTest.ControllerPagina.class})
class PaginaJsonTest {

    @RestController
    static class ControllerPagina {
        @GetMapping("/prueba/pagina")
        Page<String> pagina(Pageable pageable) {
            return new PageImpl<>(List.of("a", "b"), pageable, 45);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void pagina_serializaComoDtoEstable() throws Exception {
        mvc.perform(get("/prueba/pagina").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(45))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }
}
