package co.livorno.controlstock.analitica.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B7, fase 5: consultas de ABC y reorden sobre una instantánea armada a mano, y recálculo de punta a punta por HTTP.
 * Las consultas filtran por {@code q} = sufijo único para aislarse de otros datos de la base de pruebas.
 *
 * <p>Catálogo: v1 (clase A, ROP 10, disponible 5), v2 (clase C, sin ROP, mínimo 3, disponible 8), v3 inactiva y v4 de un
 * producto inactivo (ambas con fila en la instantánea, no deben aparecer).
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AnaliticaConsultasIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired java.time.Clock clock;
    @Autowired AnaliticaVarianteRepository instantanea;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> variantes = new ArrayList<>();
    private final List<Long> planes = new ArrayList<>();
    private long idEjecucionBase;
    private Long usuarioId;
    private Long ejecucionManual;
    private String sufijo;
    private Long v1;
    private Long v2;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "ana" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Admin Analitica', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        idEjecucionBase = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ejecucion_analitica", Long.class);

        Long activo = producto("A", true);
        Long inactivo = producto("I", false);
        v1 = variante(activo, "S", true, 5, 2);
        v2 = variante(activo, "M", true, 8, 3);
        Long v3 = variante(activo, "L", false, 9, 1);
        Long v4 = variante(inactivo, "S", true, 9, 1);

        jdbc.update("INSERT INTO ejecucion_analitica (origen, iniciada_en, terminada_en, estado, fecha_corte)"
                + " VALUES ('NOCTURNA', ?, ?, 'EXITOSA', '2026-10-14')", LocalDateTime.now(clock).minusMinutes(5),
                LocalDateTime.now(clock).minusMinutes(4));
        Long ejecucion = jdbc.queryForObject("SELECT MAX(id) FROM ejecucion_analitica", Long.class);
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 14, 2, 0);
        // lo que aplica la ejecución real a la variante (estadoStock se calcula con el ROP de la variante)
        jdbc.update("UPDATE variante SET punto_reorden = 10, clase_abc = 'A' WHERE id = ?", v1);
        instantanea.save(fila(v1, ejecucion, 1, 70, "700.00", "0.700000", "0.700000", 'A', 10, MetodoReorden.NORMAL,
                ahora));
        instantanea.save(fila(v2, ejecucion, 2, 30, "300.00", "0.300000", "1.000000", 'C', null,
                MetodoReorden.SIN_HISTORIA, ahora));
        instantanea.save(fila(v3, ejecucion, 3, 5, "50.00", "0.050000", "1.000000", 'B', 4, MetodoReorden.POISSON,
                ahora));
        instantanea.save(fila(v4, ejecucion, 4, 5, "50.00", "0.050000", "1.000000", 'B', 4, MetodoReorden.POISSON,
                ahora));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pronostico");
        jdbc.update("DELETE FROM analitica_producto_mes");
        jdbc.update("DELETE FROM analitica_producto");
        jdbc.update("DELETE FROM analitica_variante WHERE ejecucion_id > ?", idEjecucionBase);
        for (Long v : variantes) {
            jdbc.update("DELETE FROM detalle_plan WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM reserva WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long plan : planes) {
            jdbc.update("DELETE FROM plan_compra WHERE id = ?", plan);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id > ?", idEjecucionBase);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private void pedido(String estado, Long varianteId, int cantidad) {
        jdbc.update("INSERT INTO plan_compra (estado, origen, creado_en, creado_por, aprobado_en, aprobado_por, version,"
                + " actualizado_en)"
                + " VALUES (?, 'MANUAL', NOW(6), ?, IF(? = 'APROBADO', NOW(6), NULL), IF(? = 'APROBADO', ?, NULL), 0, NOW(6))",
                estado, usuarioId, estado, estado, usuarioId);
        Long plan = jdbc.queryForObject("SELECT MAX(id) FROM plan_compra", Long.class);
        planes.add(plan);
        jdbc.update("INSERT INTO detalle_plan (plan_id, variante_id, cantidad_aprobada, costo_estimado)"
                + " VALUES (?, ?, ?, 10.00)", plan, varianteId, cantidad);
    }

    private Long producto(String marca, boolean activo) {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = marca + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, 10, ?, NOW(6), NOW(6))",
                cod, categoria, activo);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, boolean activa, int fisico, int minimo) {
        String sku = "X" + sufijo + "-" + productoId + "-" + talla;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, stock_minimo,"
                + " activo, creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, ?, ?, ?, NOW(6))",
                productoId, sku, talla, fisico, minimo, activa, LocalDateTime.of(2020, 1, 1, 8, 0));
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        variantes.add(id);
        return id;
    }

    private static AnaliticaVariante fila(Long varianteId, Long ejecucionId, int posicion, int unidades,
            String ingreso, String participacion, String acumulado, char clase, Integer rop, MetodoReorden metodo,
            LocalDateTime ahora) {
        return new AnaliticaVariante(varianteId, ejecucionId, unidades, new BigDecimal(ingreso),
                new BigDecimal(participacion), new BigDecimal(acumulado), posicion, clase, 90,
                new BigDecimal("1.5000"), new BigDecimal("0.8000"), 10, new BigDecimal("0.950"),
                new BigDecimal("1.65"), rop != null ? new BigDecimal("5.10") : null, rop, metodo, ahora);
    }

    private Authentication como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "ana@livorno.test", "Admin Analitica", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    // ---------- ABC ----------

    @Test
    void abc_filtraPorTextoExcluyeInactivasYOrdenaPorPosicion() throws Exception {
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1))
                .andExpect(jsonPath("$.content[0].posicion").value(1))
                .andExpect(jsonPath("$.content[0].claseAbc").value("A"))
                .andExpect(jsonPath("$.content[0].participacion").value(70.0))
                .andExpect(jsonPath("$.content[0].acumulado").value(70.0))
                .andExpect(jsonPath("$.content[0].ingresoNeto").value(700.0))
                .andExpect(jsonPath("$.content[1].varianteId").value(v2))
                .andExpect(jsonPath("$.content[1].acumulado").value(100.0));
    }

    @Test
    void abc_filtroPorClase_devuelveSoloEsaClase() throws Exception {
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("clase", "C")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v2));
        // la clase B solo la tienen las variantes inactivas
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("clase", "B")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void abc_filtroPorCategoria_excluyeOtrasCategorias() throws Exception {
        Long otraCategoria = jdbc.queryForObject("SELECT MAX(id) FROM categoria", Long.class);
        Long propia = jdbc.queryForObject("SELECT categoria_id FROM producto WHERE id = ?", Long.class,
                productos.get(0));
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("categoriaId", String.valueOf(propia))
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        if (!otraCategoria.equals(propia)) {
            mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("categoriaId", String.valueOf(otraCategoria))
                            .with(authentication(como("ADMIN"))))
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }
    }

    @Test
    void abc_paginaYOrdenaPorSkuYIngreso() throws Exception {
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("size", "1").param("page", "1")
                        .param("sort", "ingresoNeto,asc").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1));
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).param("sort", "sku,asc")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.content[0].varianteId").value(v2));
    }

    @Test
    void abc_ordenFueraDeLaListaBlanca_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/abc").param("sort", "variante.producto.precioCompra")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/analitica/reorden").param("sort", "posicion").with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumenAbc_ultimaEjecucionExitosaYVentana() throws Exception {
        mvc.perform(get("/api/analitica/abc/resumen").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejecucion.estado").value("EXITOSA"))
                .andExpect(jsonPath("$.ventanaDesde").value("2025-10-14"))
                .andExpect(jsonPath("$.ventanaHasta").value("2026-10-13"))
                .andExpect(jsonPath("$.clases.length()").value(3))
                .andExpect(jsonPath("$.clases[0].clase").value("A"))
                .andExpect(jsonPath("$.clases[2].clase").value("C"));
    }

    // ---------- reorden ----------

    @Test
    void reorden_umbralEfectivoYFuente() throws Exception {
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("sort", "puntoReorden,desc")
                        .with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1))
                .andExpect(jsonPath("$.content[0].fuenteUmbral").value("ROP"))
                .andExpect(jsonPath("$.content[0].umbralEfectivo").value(10))
                .andExpect(jsonPath("$.content[0].stockDisponible").value(5))
                .andExpect(jsonPath("$.content[0].estadoStock").value("BAJO"))
                .andExpect(jsonPath("$.content[1].varianteId").value(v2))
                .andExpect(jsonPath("$.content[1].fuenteUmbral").value("STOCK_MINIMO"))
                .andExpect(jsonPath("$.content[1].umbralEfectivo").value(3))
                .andExpect(jsonPath("$.content[1].puntoReorden").doesNotExist())
                .andExpect(jsonPath("$.content[1].estadoStock").value("OK"));
    }

    @Test
    void reorden_enAlerta_comparaDisponibleConElUmbralEfectivo() throws Exception {
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "true")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1));
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "false")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v2));
    }

    @Test
    void reorden_enAlerta_usaLaPosicionConLoQueVieneEnCamino() throws Exception {
        // v1: disponible 5 <= ROP 10, pero con 6 en camino la posición es 11 > 10: ya no está en alerta
        pedido("APROBADO", v1, 6);
        // un borrador no cuenta como en camino: v2 (disponible 8, mínimo 3) sigue sin alerta
        pedido("BORRADOR", v2, 1);
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "true")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "false")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void reorden_enAlerta_pedidoInsuficienteNoQuitaLaAlerta() throws Exception {
        pedido("APROBADO", v1, 5); // posición 10 <= 10
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "true")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1));
    }

    @Test
    void reorden_enAlerta_agotadoConPedidoEnCaminoSiAparece() throws Exception {
        Long productoId = jdbc.queryForObject("SELECT producto_id FROM variante WHERE id = ?", Long.class, v1);
        Long agotada = variante(productoId, "XL", true, 0, 3);
        instantanea.save(fila(agotada, jdbc.queryForObject("SELECT MAX(id) FROM ejecucion_analitica", Long.class), 5,
                1, "10.00", "0.010000", "1.000000", 'C', null, MetodoReorden.SIN_HISTORIA,
                LocalDateTime.of(2026, 10, 14, 2, 0)));
        pedido("APROBADO", agotada, 500); // posición enorme, pero AGOTADO se mide sobre el disponible
        pedido("APROBADO", v1, 6);        // v1 sale de la alerta
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "true")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(agotada));
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("enAlerta", "false")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void reorden_filtrosDeMetodoYClase() throws Exception {
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("metodo", "SIN_HISTORIA")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v2));
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("clase", "A")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1));
        // POISSON solo lo tienen las variantes inactivas
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("metodo", "POISSON")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void reorden_paginaYOrdenaPorPuntoReorden() throws Exception {
        mvc.perform(get("/api/analitica/reorden").param("q", sufijo).param("size", "1")
                        .param("sort", "puntoReorden,desc").with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].varianteId").value(v1));
    }

    // ---------- última ejecución y recálculo ----------

    @Test
    void recalcular_admin_ejecutaDePuntaAPunta() throws Exception {
        String cuerpo = mvc.perform(post("/api/analitica/recalcular").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.usuario").value("Admin Analitica"))
                .andExpect(jsonPath("$.estado").value("EXITOSA"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(cuerpo, "$.id");
        ejecucionManual = id.longValue();
        assertThat(JsonPath.<Integer>read(cuerpo, "$.variantesAnalizadas")).isGreaterThanOrEqualTo(2);
        assertThat(JsonPath.<Number>read(cuerpo, "$.porcentajeAlertaStock")).isNotNull();

        // la instantánea ahora es la de la ejecución manual y las consultas la reflejan
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ejecucionManual))
                .andExpect(jsonPath("$.usuario").value("Admin Analitica"));
        assertThat(jdbc.queryForObject("SELECT ejecucion_id FROM analitica_variante WHERE variante_id = ?",
                Long.class, v1)).isEqualTo(ejecucionManual);
        mvc.perform(get("/api/analitica/abc").param("q", sufijo).with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/analitica/abc/resumen").with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.ejecucion.id").value(ejecucionManual));
    }

    @Test
    void recalcular_otrosRoles_devuelve403SinEjecutar() throws Exception {
        for (String rol : List.of("INVENTARIO", "CONTABILIDAD", "VENDEDOR")) {
            mvc.perform(post("/api/analitica/recalcular").with(authentication(como(rol))))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ejecucion_analitica WHERE origen = 'MANUAL' AND id > ?",
                Integer.class, idEjecucionBase)).isZero();
    }

    @Test
    void ultimaEjecucion_vendedor_devuelve403() throws Exception {
        mvc.perform(get("/api/analitica/ejecuciones/ultima").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
    }
}
