package co.livorno.controlstock.compra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B11, fase 6: crear, aprobar y recibir por HTTP; el JSON usa los nombres de {@code frontend/src/tipos/compras.ts}. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PlanCompraHttpIntegracionTest {

    private static final String BASE = "/api/compras/planes";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> variantes = new ArrayList<>();
    private String sufijo;
    private Long usuarioId;
    private Long producto;
    private Long v1;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "ph" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Compradora', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = "H" + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 20000.00, 100000.00, 10, TRUE, NOW(6),"
                + " NOW(6))", cod, categoria);
        producto = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        String sku = "H" + sufijo + "-S-NEG";
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " activo, creado_en, actualizado_en) VALUES (?, ?, 'S', 'NEGRO', 0, 0, 2, TRUE, NOW(6), NOW(6))",
                producto, sku);
        v1 = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        variantes.add(v1);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (SELECT id FROM plan_compra WHERE creado_por = ?)",
                usuarioId);
        jdbc.update("DELETE FROM plan_compra WHERE creado_por = ?", usuarioId);
        for (Long v : variantes) {
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        jdbc.update("DELETE FROM producto WHERE id = ?", producto);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Authentication como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "ph@livorno.test", "Compradora", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String rol, String cuerpo) {
        return b.contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(authentication(como(rol)));
    }

    @Test
    void flujo_crearAprobarRecibir_devuelveElContratoDelFrontend() throws Exception {
        // dado un plan manual creado por INVENTARIO
        String creado = mvc.perform(json(post(BASE), "INVENTARIO",
                        "{\"observacion\":\"Pedido\",\"lineas\":[{\"varianteId\":" + v1 + ",\"cantidad\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.startsWith("PC-")))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.version").isNumber())
                .andExpect(jsonPath("$.lineas[0].sku").value("H" + sufijo + "-S-NEG"))
                .andExpect(jsonPath("$.lineas[0].cantidadAprobada").value(10))
                .andExpect(jsonPath("$.lineas[0].cantidadSugerida").isEmpty())
                .andExpect(jsonPath("$.lineas[0].costoEstimado").value(20000.0))
                .andExpect(jsonPath("$.lineas[0].varianteActiva").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(creado, "$.id")).longValue();
        int version = com.jayway.jsonpath.JsonPath.read(creado, "$.version");
        long detalleId = ((Number) com.jayway.jsonpath.JsonPath.read(creado, "$.lineas[0].id")).longValue();

        // cuando ADMIN aprueba
        mvc.perform(json(post(BASE + "/" + id + "/aprobar"), "ADMIN", "{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.aprobadoPor").value("Compradora"))
                .andExpect(jsonPath("$.llegadaEsperada").isNotEmpty())
                .andExpect(jsonPath("$.lineas[0].enCaminoActual").value(10));
        mvc.perform(get(BASE + "?estado=APROBADO&size=100&sort=creadoEn,desc").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].lineas").value(1))
                .andExpect(jsonPath("$.content[?(@.id==" + id + ")].unidades").value(10))
                .andExpect(jsonPath("$.page.totalElements").isNumber());

        // INVENTARIO no puede cancelar un plan aprobado
        mvc.perform(json(post(BASE + "/" + id + "/cancelar"), "INVENTARIO", "{\"motivo\":\"Ya no se necesita\"}"))
                .andExpect(status().isForbidden());

        // y recibe: stock = kardex, plan RECIBIDO
        mvc.perform(json(post(BASE + "/" + id + "/recibir"), "INVENTARIO",
                        "{\"lineas\":[{\"detalleId\":" + detalleId + ",\"cantidadRecibida\":10,\"costoUnitario\":21000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.lineas[0].cantidadRecibida").value(10))
                .andExpect(jsonPath("$.lineas[0].costoRecibido").value(21000.0))
                .andExpect(jsonPath("$.lineas[0].enCaminoActual").value(0));

        // entonces
        assertThat(jdbc.queryForObject("SELECT stock_fisico FROM variante WHERE id = ?", Integer.class, v1))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT precio_compra FROM producto WHERE id = ?", BigDecimal.class, producto))
                .isEqualByComparingTo("21000.00");

        // y un segundo recibir responde 409
        mvc.perform(json(post(BASE + "/" + id + "/recibir"), "ADMIN",
                        "{\"lineas\":[{\"detalleId\":" + detalleId + ",\"cantidadRecibida\":10,\"costoUnitario\":21000}]}"))
                .andExpect(status().isConflict());
    }
}
