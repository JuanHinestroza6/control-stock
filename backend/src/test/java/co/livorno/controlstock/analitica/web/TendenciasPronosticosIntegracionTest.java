package co.livorno.controlstock.analitica.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * B10, fase 6: tendencias y pronósticos contra MySQL real. Ventas en 2018-2019 (meses completos) con categorías
 * propias; los listados se aíslan con {@code q} = sufijo único.
 *
 * <p>Producto P1 (categoría A, activo, SES) con variantes M y L; P2 (categoría A, activo, SIN_HISTORIA); P3 (activo,
 * categoría B, sin instantánea); P4 (inactivo, con instantánea). Ventas: abril 2018 3 u; abril 2019 6 u (100) y 2 u
 * de P3 (50); mayo 2019 4 u con 1 devuelta y una venta anulada de 5 u.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TendenciasPronosticosIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String sufijo;
    private Long usuarioId;
    private Long categoriaA;
    private Long categoriaB;
    private Long p1;
    private Long p2;
    private Long p3;
    private Long p4;
    private Long vM;
    private Long vL;
    private Long v3;
    private Long ejecucion;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "tp" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Tendencias', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        categoriaA = categoria("TA" + sufijo);
        categoriaB = categoria("TB" + sufijo);
        p1 = producto("PA" + sufijo, categoriaA, true);
        p2 = producto("PB" + sufijo, categoriaA, true);
        p3 = producto("PC" + sufijo, categoriaB, true);
        p4 = producto("PD" + sufijo, categoriaA, false);
        vM = variante(p1, "PA" + sufijo + "-M-NEG", "M");
        vL = variante(p1, "PA" + sufijo + "-L-NEG", "L");
        v3 = variante(p3, "PC" + sufijo + "-M-NEG", "M");

        Long vA = venta(LocalDateTime.of(2018, 4, 10, 10, 0), false);
        detalle(vA, vM, 3, "100.00");
        Long vB = venta(LocalDateTime.of(2019, 4, 10, 10, 0), false);
        detalle(vB, vM, 6, "100.00");
        detalle(vB, v3, 2, "50.00");
        Long vC = venta(LocalDateTime.of(2019, 5, 31, 23, 30), false);
        Long detalleC = detalle(vC, vM, 4, "100.00");
        jdbc.update("INSERT INTO devolucion (detalle_venta_id, cantidad, motivo, apta_para_venta, fecha, usuario_id)"
                + " VALUES (?, 1, 'prueba', TRUE, ?, ?)", detalleC, LocalDateTime.of(2019, 6, 3, 9, 0), usuarioId);
        Long vD = venta(LocalDateTime.of(2019, 5, 12, 10, 0), true);
        detalle(vD, vM, 5, "100.00");

        sembrarPronostico();
    }

    private void sembrarPronostico() {
        jdbc.update("INSERT INTO ejecucion_analitica (origen, iniciada_en, terminada_en, estado, fecha_corte)"
                + " VALUES ('NOCTURNA', ?, ?, 'EXITOSA', '2026-10-14')", LocalDateTime.of(2026, 10, 14, 2, 0),
                LocalDateTime.of(2026, 10, 14, 2, 1));
        ejecucion = jdbc.queryForObject("SELECT MAX(id) FROM ejecucion_analitica", Long.class);
        LocalDateTime calc = LocalDateTime.of(2026, 10, 14, 2, 0);
        insertarProducto(p1, "SES", "0.30", "0.1500", 20, 120, "131.50", calc);
        insertarProducto(p2, "SIN_HISTORIA", null, null, 2, 0, "0.00", calc);
        insertarProducto(p4, "SES", "0.20", "0.2000", 12, 50, "50.00", calc);
        jdbc.update("INSERT INTO analitica_producto_mes (producto_id, periodo, ejecucion_id, unidades_observadas,"
                + " disponibilidad, unidades_corregidas, backtest_ses, backtest_hw) VALUES"
                + " (?, '2026-08-01', ?, 9, 0.9000, 10.00, 9.50, NULL), (?, '2026-09-01', ?, 12, 1.0000, 12.00, NULL, NULL)",
                p1, ejecucion, p1, ejecucion);
        for (LocalDate periodo : List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1))) {
            insertarPronostico(p1, null, periodo, "10.00", calc);
            insertarPronostico(p1, vM, periodo, "6.00", calc);
            insertarPronostico(p1, vL, periodo, "4.00", calc);
        }
    }

    private void insertarProducto(Long producto, String metodo, String alfa, String wape, int meses, int obs,
            String corregidas, LocalDateTime calc) {
        jdbc.update("INSERT INTO analitica_producto (producto_id, ejecucion_id, meses_historia, metodo, alfa, error_wape,"
                + " wape_ses, unidades_observadas_12m, unidades_corregidas_12m, calculado_en)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", producto, ejecucion, meses, metodo,
                alfa != null ? new BigDecimal(alfa) : null, wape != null ? new BigDecimal(wape) : null,
                wape != null ? new BigDecimal(wape) : null, obs, new BigDecimal(corregidas), calc);
    }

    private void insertarPronostico(Long producto, Long variante, LocalDate periodo, String cantidad,
            LocalDateTime calc) {
        jdbc.update("INSERT INTO pronostico (producto_id, variante_id, periodo, cantidad_proyectada, metodo,"
                + " error_wape, calculado_en, ejecucion_id) VALUES (?, ?, ?, ?, 'SES', 0.1500, ?, ?)", producto,
                variante, periodo, new BigDecimal(cantidad), calc, ejecucion);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pronostico WHERE ejecucion_id = ?", ejecucion);
        jdbc.update("DELETE FROM analitica_producto_mes WHERE ejecucion_id = ?", ejecucion);
        jdbc.update("DELETE FROM analitica_producto WHERE ejecucion_id = ?", ejecucion);
        jdbc.update("DELETE FROM ejecucion_analitica WHERE id = ?", ejecucion);
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN (?, ?, ?)", vM, vL, v3);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM variante WHERE producto_id IN (?, ?, ?, ?)", p1, p2, p3, p4);
        jdbc.update("DELETE FROM producto WHERE id IN (?, ?, ?, ?)", p1, p2, p3, p4);
        jdbc.update("DELETE FROM categoria WHERE id IN (?, ?)", categoriaA, categoriaB);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long categoria(String nombre) {
        jdbc.update("INSERT INTO categoria (nombre, creado_en, actualizado_en) VALUES (?, NOW(6), NOW(6))", nombre);
        return jdbc.queryForObject("SELECT id FROM categoria WHERE nombre = ?", Long.class, nombre);
    }

    private Long producto(String codigo, Long categoria, boolean activo) {
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 10.00, 100.00, ?, NOW(6), NOW(6))",
                codigo, "Prod " + codigo, categoria, activo);
        return jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
    }

    private Long variante(Long productoId, String sku, String talla) {
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, TRUE, NOW(6), NOW(6))",
                productoId, sku, talla);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private Long venta(LocalDateTime fecha, boolean anulada) {
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado, anulada_en, anulada_por, motivo_anulacion)"
                + " VALUES (?, ?, 0, ?, ?, ?, ?)", fecha, usuarioId, anulada ? "ANULADA" : "REGISTRADA",
                anulada ? fecha.plusHours(1) : null, anulada ? usuarioId : null, anulada ? "prueba" : null);
        return jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
    }

    private Long detalle(Long ventaId, Long varianteId, int cantidad, String precio) {
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, ?, 10.00)", ventaId, varianteId, cantidad,
                new BigDecimal(precio));
        return jdbc.queryForObject("SELECT id FROM detalle_venta WHERE venta_id = ? AND variante_id = ?", Long.class,
                ventaId, varianteId);
    }

    private Authentication como(String rol) {
        var principal = new UsuarioAutenticado(usuarioId, "tp@livorno.test", "Tendencias", rol);
        return UsernamePasswordAuthenticationToken.authenticated(principal, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    // ---------- tendencias ----------

    @Test
    void tendencias_porCategoria_comparaConElAnioAnteriorYExcluyeAnuladasYOtrasCategorias() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-03").param("hasta", "2019-05")
                        .param("categoriaId", String.valueOf(categoriaA)).with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos.length()").value(3))
                .andExpect(jsonPath("$.puntos[0].periodo").value("2019-03"))
                .andExpect(jsonPath("$.puntos[0].unidades").value(0))
                .andExpect(jsonPath("$.puntos[0].variacionUnidades").doesNotExist())
                .andExpect(jsonPath("$.puntos[1].periodo").value("2019-04"))
                .andExpect(jsonPath("$.puntos[1].unidades").value(6))
                .andExpect(jsonPath("$.puntos[1].ingreso").value(600.0))
                .andExpect(jsonPath("$.puntos[1].unidadesAnioAnterior").value(3))
                .andExpect(jsonPath("$.puntos[1].ingresoAnioAnterior").value(300.0))
                .andExpect(jsonPath("$.puntos[1].variacionUnidades").value(100.0))
                .andExpect(jsonPath("$.puntos[1].variacionIngreso").value(100.0))
                // mayo: 4 vendidas - 1 devuelta (aunque la devolución sea de junio); la anulada no cuenta
                .andExpect(jsonPath("$.puntos[2].unidades").value(3))
                .andExpect(jsonPath("$.puntos[2].ingreso").value(300.0))
                .andExpect(jsonPath("$.puntos[2].variacionUnidades").doesNotExist())
                .andExpect(jsonPath("$.totalUnidades").value(9))
                .andExpect(jsonPath("$.totalIngreso").value(900.0))
                .andExpect(jsonPath("$.categoriaId").value(categoriaA));
    }

    @Test
    void tendencias_porProducto_filtraSoloEseProducto() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04").param("hasta", "2019-04")
                        .param("productoId", String.valueOf(p3)).with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos[0].unidades").value(2))
                .andExpect(jsonPath("$.puntos[0].ingreso").value(100.0));
    }

    @Test
    void tendencias_categoriaInexistente_devuelveSerieEnCeros() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04").param("hasta", "2019-05")
                        .param("categoriaId", "999999999").with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnidades").value(0))
                .andExpect(jsonPath("$.puntos.length()").value(2));
    }

    @Test
    void tendencias_sinParametros_devuelve24Meses() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").param("productoId", String.valueOf(p1))
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos.length()").value(24));
    }

    @Test
    void tendencias_masDe36Meses_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2016-01").param("hasta", "2019-05")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-05").param("hasta", "2019-04")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tendencias_vendedor_devuelve403() throws Exception {
        mvc.perform(get("/api/analitica/tendencias").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
    }

    // ---------- listado de pronósticos ----------

    @Test
    void pronosticos_listaActivosConInstantaneaYSusProximos() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).with(authentication(como("CONTABILIDAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].productoId").value(p1))
                .andExpect(jsonPath("$.content[0].metodo").value("SES"))
                .andExpect(jsonPath("$.content[0].categoria").value("TA" + sufijo))
                .andExpect(jsonPath("$.content[0].errorWape").value(0.15))
                .andExpect(jsonPath("$.content[0].unidadesUltimos12m").value(120))
                .andExpect(jsonPath("$.content[0].unidadesCorregidas12m").value(131.5))
                .andExpect(jsonPath("$.content[0].proximos.length()").value(3))
                .andExpect(jsonPath("$.content[0].proximos[0].periodo").value("2026-10"))
                .andExpect(jsonPath("$.content[0].proximos[2].periodo").value("2026-12"))
                .andExpect(jsonPath("$.content[0].proximos[0].cantidad").value(10.0))
                .andExpect(jsonPath("$.content[1].productoId").value(p2))
                .andExpect(jsonPath("$.content[1].metodo").doesNotExist())
                .andExpect(jsonPath("$.content[1].proximos.length()").value(0));
    }

    @Test
    void pronosticos_filtrosOrdenYPaginacion() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).param("metodo", "SES")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productoId").value(p1));
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).param("metodo", "HOLT_WINTERS")
                        .with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo)
                        .param("categoriaId", String.valueOf(categoriaB)).with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).param("sort", "unidadesUltimos12m,asc")
                        .param("size", "1").param("page", "1").with(authentication(como("ADMIN"))))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].productoId").value(p1));
    }

    @Test
    void pronosticos_ordenFueraDeLaListaBlanca_devuelve400() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos").param("sort", "precioCompra")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    // ---------- detalle ----------

    @Test
    void pronostico_detalle_devuelveSerieParametrosYDesagregacion() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos/" + p1).with(authentication(como("INVENTARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").value("SES"))
                .andExpect(jsonPath("$.parametros.alfa").value(0.3))
                .andExpect(jsonPath("$.parametros.beta").doesNotExist())
                .andExpect(jsonPath("$.calculadoEn").exists())
                .andExpect(jsonPath("$.serie.length()").value(2))
                .andExpect(jsonPath("$.serie[0].periodo").value("2026-08"))
                .andExpect(jsonPath("$.serie[0].observada").value(9))
                .andExpect(jsonPath("$.serie[0].disponibilidad").value(0.9))
                .andExpect(jsonPath("$.serie[0].corregida").value(10.0))
                .andExpect(jsonPath("$.serie[0].backtestSes").value(9.5))
                .andExpect(jsonPath("$.serie[0].backtestHw").doesNotExist())
                .andExpect(jsonPath("$.pronostico.length()").value(3))
                .andExpect(jsonPath("$.variantes.length()").value(2))
                .andExpect(jsonPath("$.variantes[0].varianteId").value(vM))
                .andExpect(jsonPath("$.variantes[0].talla").value("M"))
                .andExpect(jsonPath("$.variantes[0].participacion").value(0.6))
                .andExpect(jsonPath("$.variantes[0].pronostico.length()").value(3))
                .andExpect(jsonPath("$.variantes[1].varianteId").value(vL))
                .andExpect(jsonPath("$.variantes[1].participacion").value(0.4));
    }

    @Test
    void pronostico_detalle_sinHistoria_metodoNuloYSinPronostico() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos/" + p2).with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metodo").doesNotExist())
                .andExpect(jsonPath("$.parametros").doesNotExist())
                .andExpect(jsonPath("$.pronostico.length()").value(0))
                .andExpect(jsonPath("$.variantes.length()").value(0));
    }

    @Test
    void pronostico_detalle_productoSinInstantaneaOInexistente_devuelve404() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos/" + p3).with(authentication(como("ADMIN"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/analitica/pronosticos/999999999").with(authentication(como("ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void pronosticos_vendedor_devuelve403() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos").with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analitica/pronosticos/" + p1).with(authentication(como("VENDEDOR"))))
                .andExpect(status().isForbidden());
    }

    // ---------- cobertura adicional ----------

    @Test
    void tendencias_categoriaYProductoCombinados_aplicanAmbosFiltros() throws Exception {
        // P1 pertenece a la categoría A: 6 u en abril de 2019
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04").param("hasta", "2019-04")
                        .param("categoriaId", String.valueOf(categoriaA)).param("productoId", String.valueOf(p1))
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos[0].unidades").value(6))
                .andExpect(jsonPath("$.productoId").value(p1))
                .andExpect(jsonPath("$.categoriaId").value(categoriaA));
        // P3 es de la categoría B: la combinación con la categoría A no devuelve nada
        mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04").param("hasta", "2019-04")
                        .param("categoriaId", String.valueOf(categoriaA)).param("productoId", String.valueOf(p3))
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnidades").value(0))
                .andExpect(jsonPath("$.puntos[0].unidades").value(0));
    }

    @Test
    void pronosticos_tamanoFueraDeRango_seAcotaAlMaximoYUsaElPorDefectoSiEsInvalido() throws Exception {
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).param("size", "100000")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
        mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo).param("size", "0")
                        .with(authentication(como("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void respuestas_noExponenCamposDeCostoNiMargen() throws Exception {
        String tendencias = mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04")
                        .param("hasta", "2019-05").with(authentication(como("CONTABILIDAD"))))
                .andReturn().getResponse().getContentAsString();
        String listado = mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo)
                        .with(authentication(como("CONTABILIDAD")))).andReturn().getResponse().getContentAsString();
        String detalle = mvc.perform(get("/api/analitica/pronosticos/" + p1)
                        .with(authentication(como("CONTABILIDAD")))).andReturn().getResponse().getContentAsString();
        for (String json : List.of(tendencias, listado, detalle)) {
            assertThat(json.toLowerCase()).doesNotContain("costo", "preciocompra", "margen", "utilidad");
        }
    }

    @Test
    void respuestas_traenExactamenteLasClavesDelContratoDelFrontend() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var t = mapper.readTree(mvc.perform(get("/api/analitica/tendencias").param("desde", "2019-04")
                        .param("hasta", "2019-04").with(authentication(como("ADMIN")))).andReturn().getResponse()
                .getContentAsString());
        assertThat(nombres(t)).containsExactlyInAnyOrder("desde", "hasta", "categoriaId", "productoId", "puntos",
                "totalUnidades", "totalIngreso");
        assertThat(nombres(t.get("puntos").get(0))).containsExactlyInAnyOrder("periodo", "unidades", "ingreso",
                "unidadesAnioAnterior", "ingresoAnioAnterior", "variacionUnidades", "variacionIngreso");
        assertThat(t.get("categoriaId").isNull()).isTrue();

        var l = mapper.readTree(mvc.perform(get("/api/analitica/pronosticos").param("q", sufijo)
                        .with(authentication(como("ADMIN")))).andReturn().getResponse().getContentAsString());
        assertThat(nombres(l.get("content").get(0))).containsExactlyInAnyOrder("productoId", "codigo", "nombre",
                "categoria", "metodo", "errorWape", "wapeSes", "wapeHw", "motivoSinHw", "mesesHistoria",
                "unidadesUltimos12m", "unidadesCorregidas12m", "proximos");
        assertThat(l.get("content").get(0).get("wapeHw").isNull()).isTrue();
        assertThat(l.get("content").get(0).get("motivoSinHw").isNull()).isTrue();
        assertThat(l.get("content").get(1).get("metodo").isNull()).isTrue();
        assertThat(nombres(l.get("page"))).containsExactlyInAnyOrder("size", "number", "totalElements", "totalPages");

        var d = mapper.readTree(mvc.perform(get("/api/analitica/pronosticos/" + p1)
                        .with(authentication(como("ADMIN")))).andReturn().getResponse().getContentAsString());
        assertThat(nombres(d)).containsExactlyInAnyOrder("productoId", "codigo", "nombre", "categoria", "metodo",
                "errorWape", "wapeSes", "wapeHw", "motivoSinHw", "mesesHistoria", "unidadesUltimos12m",
                "unidadesCorregidas12m", "proximos", "parametros", "serie", "pronostico", "variantes",
                "calculadoEn");
        assertThat(nombres(d.get("parametros"))).containsExactlyInAnyOrder("alfa", "beta", "gamma");
        assertThat(nombres(d.get("serie").get(0))).containsExactlyInAnyOrder("periodo", "observada",
                "disponibilidad", "corregida", "backtestSes", "backtestHw");
        assertThat(nombres(d.get("variantes").get(0))).containsExactlyInAnyOrder("varianteId", "sku", "talla",
                "color", "participacion", "pronostico");
        assertThat(nombres(d.get("pronostico").get(0))).containsExactlyInAnyOrder("periodo", "cantidad");
    }

    private static java.util.Set<String> nombres(tools.jackson.databind.JsonNode nodo) {
        return new java.util.HashSet<>(nodo.propertyNames());
    }
}
