package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T2, fase 7: reportes de punta a punta por HTTP con datos creados por los endpoints reales (catálogo, entrada, ventas,
 * devoluciones, anulación, reserva convertida). Solo las fechas se mueven al pasado con un UPDATE de preparación.
 *
 * <p>Producto P (compra 20.000, venta 50.000) con variantes M y L, 100 unidades cada una a 20.000:
 * <ul>
 *   <li>S1 (2015-03-10): M x3 a lista.</li>
 *   <li>S2 (2015-03-20): M x3 a 45.000 y L x2 a 40.000.</li>
 *   <li>S3 (2015-03-25): M x5, anulada.</li>
 *   <li>S4 (2015-04-12): reserva de L x4 de "Cliente Prueba" convertida en venta.</li>
 *   <li>Devolución apta de 1 M de S2 (2015-04-05) y no apta de 1 L de S2 (2015-04-06).</li>
 *   <li>Una reserva ACTIVA de M x1 del mismo cliente, para comprobar que nada la filtra.</li>
 * </ul>
 * A mano, sobre 2015-03-01..2015-04-30: ventas a lista 600.000, descuentos 35.000, facturado 565.000, devoluciones
 * 85.000, neto 480.000, costo (12 - 2) x 20.000 = 200.000, margen 280.000 (58,3 %), merma no apta 20.000.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ReportesFlujoIntegracionTest {

    private static final String CLAVE = "Clave123!";
    private static final String DESDE = "2015-03-01";
    private static final String HASTA = "2015-04-30";
    private static final String RANGO = "desde=" + DESDE + "&hasta=" + HASTA;
    private static final List<String> ROLES = List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD");
    private static final List<String> PROHIBIDOS = List.of("cliente prueba", "@clienteprueba", "clienteprueba");

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired LimitadorIntentosLogin limitador;

    private final List<Long> usuariosCreados = new ArrayList<>();
    private final List<Long> ventasCreadas = new ArrayList<>();
    private final Map<String, String> tokens = new HashMap<>();
    private String sufijo;
    private Long categoriaId;
    private Long productoId;
    private String numeroAnulada;

    // ---------- preparación y limpieza ----------

    @BeforeEach
    void preparar() throws Exception {
        limitador.limpiar();
        String base = Long.toString(System.nanoTime(), 36).toUpperCase();
        sufijo = base.substring(Math.max(0, base.length() - 8));
        for (Rol rol : List.of(Rol.INVENTARIO, Rol.VENDEDOR, Rol.CONTABILIDAD)) {
            String email = rol.name().toLowerCase() + "." + sufijo + "@livorno.test";
            usuariosCreados.add(usuarios.save(new Usuario("Prueba " + rol, email, encoder.encode(CLAVE), rol)).getId());
        }
        for (String rol : ROLES) {
            tokens.put(rol, login(rol));
        }
        armarEscenario();
    }

    @AfterEach
    void limpiar() {
        if (productoId != null) {
            String variantes = "(SELECT id FROM variante WHERE producto_id = " + productoId + ")";
            jdbc.update("DELETE FROM devolucion WHERE detalle_venta_id IN (SELECT id FROM detalle_venta"
                    + " WHERE variante_id IN " + variantes + ")");
            jdbc.update("DELETE FROM reserva WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM movimiento WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM alerta WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN " + variantes);
            jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN " + variantes);
            for (Long id : ventasCreadas) {
                jdbc.update("DELETE FROM venta WHERE id = ?", id);
            }
            jdbc.update("DELETE FROM variante WHERE producto_id = ?", productoId);
            jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        }
        if (categoriaId != null) {
            jdbc.update("DELETE FROM categoria WHERE id = ?", categoriaId);
        }
        for (Long id : usuariosCreados) {
            usuarios.deleteById(id);
        }
    }

    // ---------- utilidades HTTP ----------

    private String login(String rol) throws Exception {
        limitador.limpiar();
        String email = rol.equals("ADMIN") ? "admin.prueba@livorno.test"
                : rol.toLowerCase() + "." + sufijo + "@livorno.test";
        String clave = rol.equals("ADMIN") ? "ClaveAdminPrueba123!" : CLAVE;
        String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    private MockHttpServletResponse enviar(String metodo, String ruta, String rol, String json) throws Exception {
        MockHttpServletRequestBuilder req = metodo.equals("POST") ? post(ruta) : get(ruta);
        if (rol != null) {
            req.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(rol));
        }
        if (json != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    /** Envía y exige el estado esperado; devuelve el cuerpo como texto. */
    private String ok(String metodo, String ruta, String rol, String json, int esperado) throws Exception {
        MockHttpServletResponse r = enviar(metodo, ruta, rol, json);
        assertThat(r.getStatus()).as("%s %s como %s: %s", metodo, ruta, rol, r.getContentAsString()).isEqualTo(esperado);
        return r.getContentAsString();
    }

    private byte[] bytes(String ruta, String rol) throws Exception {
        MockHttpServletResponse r = enviar("GET", ruta, rol, null);
        assertThat(r.getStatus()).as("GET %s como %s", ruta, rol).isEqualTo(200);
        return r.getContentAsByteArray();
    }

    private static long id(String cuerpo, String ruta) {
        return ((Number) JsonPath.read(cuerpo, ruta)).longValue();
    }

    private static Object leer(String cuerpo, String ruta) {
        return JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
                .parse(cuerpo).read(ruta);
    }

    private static BigDecimal num(String cuerpo, String ruta) {
        Object valor = JsonPath.read(cuerpo, ruta);
        return new BigDecimal(String.valueOf(valor));
    }

    private static long detalleDe(String venta, long varianteId) {
        List<Number> ids = JsonPath.read(venta, "$.lineas[?(@.varianteId==" + varianteId + ")].detalleId");
        return ids.get(0).longValue();
    }

    private static String linea(long varianteId, int cantidad, String precio) {
        return "{\"varianteId\":" + varianteId + ",\"cantidad\":" + cantidad
                + (precio == null ? "" : ",\"precioUnitario\":" + precio) + "}";
    }

    private String venderComoVendedor(String... lineas) throws Exception {
        String venta = ok("POST", "/api/ventas", "VENDEDOR", "{\"lineas\":[" + String.join(",", lineas) + "]}", 201);
        ventasCreadas.add(id(venta, "$.id"));
        return venta;
    }

    private void devolver(String venta, long varianteId, boolean apta, LocalDateTime fecha) throws Exception {
        String d = ok("POST", "/api/ventas/" + id(venta, "$.id") + "/devoluciones", "VENDEDOR",
                "{\"detalleVentaId\":" + detalleDe(venta, varianteId)
                        + ",\"cantidad\":1,\"motivo\":\"Devolución de prueba\",\"aptaParaVenta\":" + apta + "}", 201);
        jdbc.update("UPDATE devolucion SET fecha = ? WHERE id = ?", fecha, id(d, "$.id"));
    }

    private void fecharVenta(String venta, LocalDateTime fecha) {
        jdbc.update("UPDATE venta SET fecha = ? WHERE id = ?", fecha, id(venta, "$.id"));
    }

    // ---------- escenario por la API ----------

    private void armarEscenario() throws Exception {
        categoriaId = id(ok("POST", "/api/categorias", "ADMIN", "{\"nombre\":\"Flujo " + sufijo + "\"}", 201), "$.id");
        productoId = id(ok("POST", "/api/productos", "ADMIN", "{\"codigo\":\"FL" + sufijo
                + "\",\"nombre\":\"Camiseta Flujo " + sufijo + "\",\"categoriaId\":" + categoriaId
                + ",\"precioCompra\":20000.00,\"precioVenta\":50000.00}", 201), "$.id");
        ok("POST", "/api/productos/" + productoId + "/variantes/generar", "ADMIN",
                "{\"tallas\":[\"M\",\"L\"],\"colores\":[\"Negro\"],\"stockMinimo\":2}", 201);
        long vM = variante("M");
        long vL = variante("L");
        ok("POST", "/api/inventario/entradas", "INVENTARIO", "{\"lineas\":["
                + "{\"varianteId\":" + vM + ",\"cantidad\":100,\"costoUnitario\":20000.00},"
                + "{\"varianteId\":" + vL + ",\"cantidad\":100,\"costoUnitario\":20000.00}]}", 201);

        String s1 = venderComoVendedor(linea(vM, 3, null));
        fecharVenta(s1, LocalDateTime.of(2015, 3, 10, 10, 0));

        String s2 = venderComoVendedor(linea(vM, 3, "45000.00"), linea(vL, 2, "40000.00"));
        fecharVenta(s2, LocalDateTime.of(2015, 3, 20, 10, 0));
        devolver(s2, vM, true, LocalDateTime.of(2015, 4, 5, 10, 0));
        devolver(s2, vL, false, LocalDateTime.of(2015, 4, 6, 10, 0));

        String s3 = venderComoVendedor(linea(vM, 5, null));
        ok("POST", "/api/ventas/" + id(s3, "$.id") + "/anular", "ADMIN", "{\"motivo\":\"Error de digitación\"}", 200);
        jdbc.update("UPDATE venta SET fecha = ?, anulada_en = ? WHERE id = ?", LocalDateTime.of(2015, 3, 25, 10, 0),
                LocalDateTime.of(2015, 3, 25, 11, 0), id(s3, "$.id"));
        numeroAnulada = JsonPath.read(s3, "$.numero");

        String reserva = ok("POST", "/api/reservas", "VENDEDOR", "{\"clienteNombre\":\"Cliente Prueba\","
                + "\"clienteContacto\":\"@clienteprueba\",\"lineas\":[{\"varianteId\":" + vL + ",\"cantidad\":4}]}", 201);
        String s4 = ok("POST", "/api/ventas/desde-reservas", "VENDEDOR",
                "{\"reservaIds\":[" + id(reserva, "$[0].id") + "]}", 201);
        ventasCreadas.add(id(s4, "$.id"));
        fecharVenta(s4, LocalDateTime.of(2015, 4, 12, 10, 0));

        ok("POST", "/api/reservas", "VENDEDOR", "{\"clienteNombre\":\"Cliente Prueba\","
                + "\"clienteContacto\":\"@clienteprueba\",\"lineas\":[{\"varianteId\":" + vM + ",\"cantidad\":1}]}", 201);
    }

    private long variante(String talla) {
        return jdbc.queryForObject("SELECT id FROM variante WHERE producto_id = ? AND talla = ?", Long.class,
                productoId, talla);
    }

    // ---------- cifras ----------

    @Test
    void rentabilidad_ejemploAMano_cifrasDelPlan() throws Exception {
        String r = ok("GET", "/api/reportes/rentabilidad?" + RANGO + "&categoriaId=" + categoriaId
                + "&agrupacion=PRODUCTO", "ADMIN", null, 200);

        assertThat(num(r, "$.totales.ventasLista")).isEqualByComparingTo("600000.00");
        assertThat(num(r, "$.totales.descuentos")).isEqualByComparingTo("35000.00");
        assertThat(num(r, "$.totales.facturado")).isEqualByComparingTo("565000.00");
        assertThat(num(r, "$.totales.devoluciones")).isEqualByComparingTo("85000.00");
        assertThat(num(r, "$.totales.ingresoNeto")).isEqualByComparingTo("480000.00");
        assertThat(num(r, "$.totales.costoVentas")).isEqualByComparingTo("200000.00");
        assertThat(num(r, "$.totales.margenBruto")).isEqualByComparingTo("280000.00");
        assertThat(num(r, "$.totales.margenPorcentaje")).isEqualByComparingTo("58.3");
        assertThat(num(r, "$.totales.mermaNoApta")).isEqualByComparingTo("20000.00");
        assertThat(num(r, "$.totales.resultadoDespuesMermas")).isEqualByComparingTo("260000.00");
        assertThat(id(r, "$.totales.unidadesVendidas")).isEqualTo(12);
        assertThat(id(r, "$.totales.unidadesDevueltas")).isEqualTo(2);
        assertThat(id(r, "$.totales.unidadesNetas")).isEqualTo(10);
        assertThat((List<?>) JsonPath.read(r, "$.filas")).hasSize(1);
        assertThat(id(r, "$.filas[0].id")).isEqualTo(productoId);
        assertThat(num(r, "$.filas[0].ingresoNeto")).isEqualByComparingTo("480000.00");
    }

    @Test
    void libro_ejemploAMano_mesesYDiasConDevolucionEnLaFechaDeLaDevolucion() throws Exception {
        String mes = ok("GET", "/api/reportes/libro-ventas?" + RANGO + "&agrupacion=MES", "CONTABILIDAD", null, 200);
        String dia = ok("GET", "/api/reportes/libro-ventas?" + RANGO + "&agrupacion=DIA", "CONTABILIDAD", null, 200);

        assertThat((List<?>) JsonPath.read(mes, "$.filas")).hasSize(2);
        assertThat((String) JsonPath.read(mes, "$.filas[0].periodo")).isEqualTo("2015-03");
        assertThat(id(mes, "$.filas[0].numeroVentas")).isEqualTo(2);
        assertThat(num(mes, "$.filas[0].facturado")).isEqualByComparingTo("365000.00");
        assertThat(num(mes, "$.filas[0].devoluciones")).isEqualByComparingTo("0.00");
        assertThat(num(mes, "$.filas[0].ingresoNeto")).isEqualByComparingTo("365000.00");
        assertThat(num(mes, "$.filas[0].costoVentas")).isEqualByComparingTo("160000.00");
        assertThat(num(mes, "$.filas[0].margenBruto")).isEqualByComparingTo("205000.00");
        assertThat((String) JsonPath.read(mes, "$.filas[1].periodo")).isEqualTo("2015-04");
        assertThat(id(mes, "$.filas[1].numeroVentas")).isEqualTo(1);
        assertThat(num(mes, "$.filas[1].facturado")).isEqualByComparingTo("200000.00");
        assertThat(num(mes, "$.filas[1].devoluciones")).isEqualByComparingTo("85000.00");
        assertThat(num(mes, "$.filas[1].ingresoNeto")).isEqualByComparingTo("115000.00");
        assertThat(num(mes, "$.filas[1].costoVentas")).isEqualByComparingTo("40000.00");
        assertThat(num(mes, "$.filas[1].margenBruto")).isEqualByComparingTo("75000.00");
        assertThat(num(mes, "$.filas[1].mermaNoApta")).isEqualByComparingTo("20000.00");

        assertThat((List<String>) JsonPath.read(dia, "$.filas[*].periodo"))
                .containsExactly("2015-03-10", "2015-03-20", "2015-04-05", "2015-04-06", "2015-04-12");
        // la devolución apta resta en su día: neto, costo y margen negativos
        assertThat(num(dia, "$.filas[2].ingresoNeto")).isEqualByComparingTo("-45000.00");
        assertThat(num(dia, "$.filas[2].costoVentas")).isEqualByComparingTo("-20000.00");
        assertThat(num(dia, "$.filas[2].margenBruto")).isEqualByComparingTo("-25000.00");
        assertThat(num(dia, "$.filas[3].mermaNoApta")).isEqualByComparingTo("20000.00");
        assertThat(num(dia, "$.filas[3].margenBruto")).isEqualByComparingTo("-20000.00");
    }

    @Test
    void libro_ventaAnulada_soloApareceEnLaSeccionDeAnuladas() throws Exception {
        String libro = ok("GET", "/api/reportes/libro-ventas?" + RANGO + "&agrupacion=MES", "ADMIN", null, 200);

        assertThat(id(libro, "$.totalAnuladas")).isEqualTo(1);
        assertThat(num(libro, "$.montoAnuladas")).isEqualByComparingTo("250000.00");
        assertThat((String) JsonPath.read(libro, "$.anuladas[0].numero")).isEqualTo(numeroAnulada);
        // las cifras no incluyen los 250.000 ni las 5 unidades de la anulada
        assertThat(num(libro, "$.totales.facturado")).isEqualByComparingTo("565000.00");
        assertThat(id(libro, "$.totales.unidadesVendidas")).isEqualTo(12);
        assertThat(id(libro, "$.totales.numeroVentas")).isEqualTo(3);
    }

    @Test
    void conciliacion_libroRentabilidadYMasVendidos_coincidenSobreElRangoCompleto() throws Exception {
        String rent = ok("GET", "/api/reportes/rentabilidad?" + RANGO + "&categoriaId=" + categoriaId
                + "&agrupacion=CATEGORIA", "ADMIN", null, 200);
        String libro = ok("GET", "/api/reportes/libro-ventas?" + RANGO + "&agrupacion=MES", "ADMIN", null, 200);
        String masVendidos = ok("GET", "/api/analitica/mas-vendidos?" + RANGO + "&categoriaId=" + categoriaId
                + "&agrupacion=PRODUCTO&criterio=INGRESO", "ADMIN", null, 200);

        assertThat(num(libro, "$.totales.ventasLista")).isEqualByComparingTo(num(rent, "$.totales.ventasLista"));
        assertThat(num(libro, "$.totales.descuentos")).isEqualByComparingTo(num(rent, "$.totales.descuentos"));
        assertThat(num(libro, "$.totales.facturado")).isEqualByComparingTo(num(rent, "$.totales.facturado"));
        assertThat(num(libro, "$.totales.devoluciones")).isEqualByComparingTo(num(rent, "$.totales.devoluciones"));
        assertThat(num(libro, "$.totales.ingresoNeto")).isEqualByComparingTo(num(rent, "$.totales.ingresoNeto"));
        assertThat(num(libro, "$.totales.costoVentas")).isEqualByComparingTo(num(rent, "$.totales.costoVentas"));
        assertThat(num(libro, "$.totales.margenBruto")).isEqualByComparingTo(num(rent, "$.totales.margenBruto"));
        assertThat(num(libro, "$.totales.mermaNoApta")).isEqualByComparingTo(num(rent, "$.totales.mermaNoApta"));
        assertThat(id(libro, "$.totales.unidadesVendidas")).isEqualTo(id(rent, "$.totales.unidadesVendidas"));
        assertThat(id(libro, "$.totales.unidadesDevueltas")).isEqualTo(id(rent, "$.totales.unidadesDevueltas"));
        // ingreso neto de rentabilidad = ingreso neto de Más vendidos del mismo rango
        assertThat(num(masVendidos, "$.totalIngreso")).isEqualByComparingTo(num(rent, "$.totales.ingresoNeto"));
        assertThat(num(masVendidos, "$.items[0].ingreso")).isEqualByComparingTo("480000.00");
        assertThat(id(masVendidos, "$.items[0].unidades")).isEqualTo(id(rent, "$.totales.unidadesNetas"));
    }

    @Test
    void valorizacion_ejemploAMano_fisicoPorCostoActualIncluyeLoReservado() throws Exception {
        // M: 100 - 3 - 3 - 5 + 5 (anulación) + 1 (devolución apta) = 95; L: 100 - 2 - 4 = 94 (la no apta no suma)
        String v = ok("GET", "/api/reportes/valorizacion?categoriaId=" + categoriaId + "&agrupacion=PRODUCTO",
                "CONTABILIDAD", null, 200);

        assertThat(id(v, "$.totales.unidadesFisicas")).isEqualTo(189);
        assertThat(id(v, "$.totales.unidadesReservadas")).isEqualTo(1);
        assertThat(num(v, "$.totales.valorCosto")).isEqualByComparingTo("3780000.00");
        assertThat(num(v, "$.totales.valorVenta")).isEqualByComparingTo("9450000.00");
    }

    // ---------- Ley 1581 ----------

    private static String textoPdf(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(lector);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= lector.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append(' ');
            }
            return sb.toString().replaceAll("\\s+", " ");
        } finally {
            lector.close();
        }
    }

    private static String textoXlsx(byte[] xlsx) throws Exception {
        DataFormatter formato = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet hoja : libro) {
                sb.append(hoja.getSheetName()).append(' ');
                for (Row fila : hoja) {
                    for (Cell celda : fila) {
                        sb.append(formato.formatCellValue(celda)).append(' ');
                    }
                }
            }
        }
        return sb.toString();
    }

    private void sinDatosDeCliente(String origen, String texto) {
        String minusculas = texto.toLowerCase();
        for (String prohibido : PROHIBIDOS) {
            assertThat(minusculas).as("%s no debe contener \"%s\"", origen, prohibido).doesNotContain(prohibido);
        }
    }

    @Test
    void exportaciones_pdfXlsxYPanel_noContienenDatosDelCliente() throws Exception {
        String filtroCat = "&categoriaId=" + categoriaId;
        Map<String, String> rutas = new HashMap<>();
        rutas.put("rentabilidad", "/api/reportes/rentabilidad/exportar?" + RANGO + filtroCat + "&agrupacion=VARIANTE");
        rutas.put("libro-ventas", "/api/reportes/libro-ventas/exportar?" + RANGO + "&agrupacion=DIA");
        rutas.put("valorizacion", "/api/reportes/valorizacion/exportar?agrupacion=VARIANTE" + filtroCat);
        // sin fechas: del día 1 del mes hasta hoy, donde están los movimientos reales del flujo (incluida la reserva)
        rutas.put("movimientos", "/api/reportes/movimientos/exportar?agrupacion=VARIANTE" + filtroCat);
        rutas.put("tendencias", "/api/analitica/tendencias/exportar?desde=2015-01&hasta=2015-04" + filtroCat);

        for (Map.Entry<String, String> e : rutas.entrySet()) {
            String rol = e.getKey().equals("tendencias") ? "INVENTARIO" : "ADMIN";
            String pdf = textoPdf(bytes(e.getValue() + "&formato=PDF", rol));
            String xlsx = textoXlsx(bytes(e.getValue() + "&formato=XLSX", rol));
            assertThat(pdf).as("PDF de %s con contenido", e.getKey()).hasSizeGreaterThan(100);
            assertThat(xlsx).as("XLSX de %s con contenido", e.getKey()).hasSizeGreaterThan(50);
            sinDatosDeCliente("PDF de " + e.getKey(), pdf);
            sinDatosDeCliente("XLSX de " + e.getKey(), xlsx);
        }
        // contenido real: el libro lleva la anulada y el detalle en Excel lleva el producto (y siguen sin cliente)
        String pdfLibro = textoPdf(bytes(rutas.get("libro-ventas") + "&formato=PDF", "CONTABILIDAD"));
        String xlsxLibro = textoXlsx(bytes(rutas.get("libro-ventas") + "&formato=XLSX", "CONTABILIDAD"));
        assertThat(pdfLibro).contains(numeroAnulada);
        assertThat(xlsxLibro).contains(numeroAnulada).contains("Camiseta Flujo " + sufijo);

        for (String rol : ROLES) {
            sinDatosDeCliente("panel de " + rol, ok("GET", "/api/panel", rol, null, 200));
        }
        sinDatosDeCliente("rentabilidad JSON", ok("GET", "/api/reportes/rentabilidad?" + RANGO + filtroCat
                + "&agrupacion=VARIANTE", "ADMIN", null, 200));
        sinDatosDeCliente("libro JSON", ok("GET", "/api/reportes/libro-ventas?" + RANGO, "ADMIN", null, 200));
        sinDatosDeCliente("movimientos JSON", ok("GET", "/api/reportes/movimientos?agrupacion=VARIANTE" + filtroCat,
                "ADMIN", null, 200));
        sinDatosDeCliente("valorizacion JSON", ok("GET", "/api/reportes/valorizacion?agrupacion=VARIANTE" + filtroCat,
                "ADMIN", null, 200));
    }

    // ---------- panel por rol ----------

    @Test
    void panel_porRol_seccionesYValoresSegunLaMatriz() throws Exception {
        String admin = ok("GET", "/api/panel", "ADMIN", null, 200);
        String inventario = ok("GET", "/api/panel", "INVENTARIO", null, 200);
        String vendedor = ok("GET", "/api/panel", "VENDEDOR", null, 200);
        String contabilidad = ok("GET", "/api/panel", "CONTABILIDAD", null, 200);

        assertThat(leer(admin, "$.ventas.margenMes")).isNotNull();
        assertThat(leer(admin, "$.inventario.valorCosto")).isNotNull();
        assertThat(leer(admin, "$.reservas")).isNotNull();
        assertThat(leer(admin, "$.compras")).isNotNull();
        assertThat(leer(inventario, "$.ventas")).isNull();
        assertThat(leer(inventario, "$.inventario.valorCosto")).isNull();
        assertThat(leer(inventario, "$.reservas")).isNotNull();
        assertThat(leer(vendedor, "$.ventas")).isNotNull();
        assertThat(leer(vendedor, "$.ventas.margenMes")).isNull();
        assertThat(leer(vendedor, "$.inventario.valorCosto")).isNull();
        assertThat(leer(vendedor, "$.compras")).isNull();
        assertThat(leer(contabilidad, "$.ventas.margenMes")).isNotNull();
        assertThat(leer(contabilidad, "$.reservas")).isNull();
        assertThat(leer(contabilidad, "$.compras.valorEnCamino")).isNotNull();
    }

    // ---------- matriz de roles ----------

    private record Fila(String nombre, String ruta, List<String> permitidos) {}

    @Test
    void matrizDeRoles_endpointsDeReportesPanelYTendencias_segunLaMatriz() throws Exception {
        List<String> admCont = List.of("ADMIN", "CONTABILIDAD");
        List<Fila> filas = List.of(
                new Fila("rentabilidad", "/api/reportes/rentabilidad?" + RANGO, admCont),
                new Fila("rentabilidad/exportar PDF", "/api/reportes/rentabilidad/exportar?" + RANGO + "&formato=PDF", admCont),
                new Fila("libro-ventas", "/api/reportes/libro-ventas?" + RANGO, admCont),
                new Fila("libro-ventas/exportar XLSX", "/api/reportes/libro-ventas/exportar?" + RANGO + "&formato=XLSX", admCont),
                new Fila("valorizacion", "/api/reportes/valorizacion?categoriaId=" + categoriaId, admCont),
                new Fila("valorizacion/exportar PDF", "/api/reportes/valorizacion/exportar?formato=PDF&categoriaId=" + categoriaId, admCont),
                new Fila("movimientos", "/api/reportes/movimientos?categoriaId=" + categoriaId, admCont),
                new Fila("movimientos/exportar XLSX", "/api/reportes/movimientos/exportar?formato=XLSX&categoriaId=" + categoriaId, admCont),
                new Fila("panel", "/api/panel", ROLES),
                new Fila("tendencias/exportar PDF", "/api/analitica/tendencias/exportar?desde=2015-01&hasta=2015-04&formato=PDF",
                        List.of("ADMIN", "INVENTARIO", "CONTABILIDAD")),
                new Fila("tendencias/exportar XLSX", "/api/analitica/tendencias/exportar?desde=2015-01&hasta=2015-04&formato=XLSX",
                        List.of("ADMIN", "INVENTARIO", "CONTABILIDAD")));

        List<String> errores = new ArrayList<>();
        for (Fila f : filas) {
            for (String rol : ROLES) {
                int esperado = f.permitidos().contains(rol) ? 200 : 403;
                int real = enviar("GET", f.ruta(), rol, null).getStatus();
                if (real != esperado) {
                    errores.add(f.nombre() + " como " + rol + ": esperado " + esperado + ", real " + real);
                }
            }
            int sinToken = enviar("GET", f.ruta(), null, null).getStatus();
            if (sinToken != 401) {
                errores.add(f.nombre() + " sin token: esperado 401, real " + sinToken);
            }
        }
        assertThat(errores).as("desviaciones de la matriz de roles (%d filas x 5)", filas.size()).isEmpty();
    }
}
