package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.servicio.LimitadorIntentosLogin;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * T1, fase 7 (RNF-03): rendimiento de reportes y consultas con ~12.000 ventas, ~18.000 líneas, ~600 devoluciones y
 * ~40.000 movimientos repartidos en 24 meses, insertados por JDBC (solo aquí) y consultados por HTTP con el JWT de cada
 * rol. Mide el peor de 5 corridas (tras 1 de calentamiento), exige menos de 3 s y revisa con EXPLAIN que las consultas
 * no recorran por completo venta, detalle_venta ni movimiento.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RendimientoReportesTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String CLAVE = "Clave123!";
    private static final long LIMITE_MS = 3000;
    private static final int CORRIDAS = 5;
    private static final int VENTAS = 12_000;
    private static final int VENTAS_ANULADAS = 150;
    private static final int DEVOLUCIONES = 600;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired LimitadorIntentosLogin limitador;
    @Autowired VentaService ventas;
    @Autowired DevolucionService devoluciones;
    @Autowired OperacionInventarioService operaciones;
    @Autowired MySQLContainer contenedor;

    private DatosReporte datos;
    private final List<Long> usuariosExtra = new ArrayList<>();
    private final List<Long> planes = new ArrayList<>();
    private final Map<String, String> tokens = new HashMap<>();
    private long varianteKardex;
    private LocalDate hoy;

    private record LineaGen(int varianteIdx, int cantidad, BigDecimal lista, BigDecimal unitario, BigDecimal costo) {}

    private record VentaGen(LocalDateTime fecha, boolean anulada, List<LineaGen> lineas) {}

    private record Ev(LocalDateTime fecha, int orden, String tipo, int cantidad, BigDecimal costo, String refTipo,
            Long refId) {}

    // ---------- datos ----------

    @BeforeAll
    void insertarDatos() {
        limitador.limpiar();
        hoy = LocalDate.now(BOGOTA);
        datos = new DatosReporte(jdbc, ventas, devoluciones, operaciones);
        String sufijo = datos.sufijo();
        Random rnd = new Random(42);

        // catálogo: 6 categorías, 50 productos, 300 variantes
        List<Long> categorias = new ArrayList<>();
        for (int c = 0; c < 6; c++) {
            categorias.add(datos.categoria("Rend" + c));
        }
        String[] tallas = {"XS", "S", "M", "L", "XL", "XXL"};
        List<Long> productoIds = new ArrayList<>();
        List<BigDecimal> precioVenta = new ArrayList<>();
        List<BigDecimal> precioCompra = new ArrayList<>();
        for (int p = 0; p < 50; p++) {
            BigDecimal venta = BigDecimal.valueOf(30_000L + 1_000L * rnd.nextInt(91)).setScale(2);
            BigDecimal compra = venta.multiply(new BigDecimal("0.40")).setScale(2, java.math.RoundingMode.HALF_UP);
            productoIds.add(datos.producto("RD" + p, categorias.get(p % 6), compra.toPlainString(),
                    venta.toPlainString()));
            precioVenta.add(venta);
            precioCompra.add(compra);
        }
        LocalDateTime creado = LocalDateTime.now(BOGOTA);
        List<Object[]> filasVar = new ArrayList<>();
        for (int p = 0; p < 50; p++) {
            for (String t : tallas) {
                filasVar.add(new Object[] {productoIds.get(p), "T1" + productoIds.get(p) + "-" + t + "-" + sufijo, t,
                        "NEGRO", 0, 0, true, creado, creado});
            }
        }
        insertar("variante", "producto_id, sku, talla, color, stock_fisico, stock_reservado, activo, creado_en,"
                + " actualizado_en", filasVar);
        List<Long> varianteIds = jdbc.queryForList("SELECT id FROM variante WHERE producto_id IN ("
                + csv(productoIds) + ") ORDER BY producto_id, id", Long.class);
        assertThat(varianteIds).hasSize(300);
        varianteKardex = varianteIds.get(7);

        // ventas generadas en memoria
        List<VentaGen> gen = new ArrayList<>();
        LocalDate primerDia = hoy.minusDays(728);
        for (int i = 0; i < VENTAS + VENTAS_ANULADAS; i++) {
            boolean anulada = i >= VENTAS;
            LocalDateTime fecha = primerDia.plusDays(rnd.nextInt(728)).atTime(9 + rnd.nextInt(11), rnd.nextInt(60),
                    rnd.nextInt(60));
            int nLineas = anulada ? 1 : (rnd.nextBoolean() ? 1 : 2);
            Set<Integer> usadas = new HashSet<>();
            List<LineaGen> lineas = new ArrayList<>();
            while (lineas.size() < nLineas) {
                int v = rnd.nextInt(300);
                if (!usadas.add(v)) {
                    continue;
                }
                int p = v / 6;
                BigDecimal lista = precioVenta.get(p);
                BigDecimal unitario = rnd.nextInt(4) == 0
                        ? lista.multiply(new BigDecimal("0.90")).setScale(0, java.math.RoundingMode.HALF_UP).setScale(2)
                        : lista;
                lineas.add(new LineaGen(v, 1 + rnd.nextInt(3), lista, unitario, precioCompra.get(p)));
            }
            gen.add(new VentaGen(fecha, anulada, lineas));
        }

        Long vendedor = datos.vendedorId;
        Long admin = datos.adminId;
        List<Object[]> filasVenta = new ArrayList<>();
        for (VentaGen v : gen) {
            BigDecimal total = BigDecimal.ZERO;
            for (LineaGen l : v.lineas()) {
                total = total.add(l.unitario().multiply(BigDecimal.valueOf(l.cantidad())));
            }
            filasVenta.add(new Object[] {v.fecha(), vendedor, null, total, v.anulada() ? "ANULADA" : "REGISTRADA",
                    v.anulada() ? v.fecha().plusHours(1) : null, v.anulada() ? admin : null,
                    v.anulada() ? "Error de digitación" : null});
        }
        insertar("venta", "fecha, usuario_id, canal, total, estado, anulada_en, anulada_por, motivo_anulacion",
                filasVenta);
        List<Long> ventaIds = jdbc.queryForList("SELECT id FROM venta WHERE usuario_id = ? ORDER BY id", Long.class,
                vendedor);
        assertThat(ventaIds).hasSize(gen.size());

        List<Object[]> filasDetalle = new ArrayList<>();
        for (int i = 0; i < gen.size(); i++) {
            for (LineaGen l : gen.get(i).lineas()) {
                filasDetalle.add(new Object[] {ventaIds.get(i), varianteIds.get(l.varianteIdx()), l.cantidad(),
                        l.lista(), l.unitario(), l.costo()});
            }
        }
        insertar("detalle_venta", "venta_id, variante_id, cantidad, precio_lista, precio_unitario, costo_unitario",
                filasDetalle);
        Map<String, Long> detalleIds = new HashMap<>();
        jdbc.query("SELECT id, venta_id, variante_id FROM detalle_venta WHERE venta_id BETWEEN ? AND ?", rs -> {
            detalleIds.put(rs.getLong("venta_id") + ":" + rs.getLong("variante_id"), rs.getLong("id"));
        }, ventaIds.get(0), ventaIds.get(ventaIds.size() - 1));

        // devoluciones: una unidad de 600 líneas distintas de ventas REGISTRADA
        LocalDateTime tope = hoy.atStartOfDay().minusMinutes(1);
        List<Object[]> filasDev = new ArrayList<>();
        Map<Integer, List<Ev>> eventos = new LinkedHashMap<>();
        for (int v = 0; v < 300; v++) {
            eventos.put(v, new ArrayList<>());
        }
        Set<String> lineasDevueltas = new HashSet<>();
        while (filasDev.size() < DEVOLUCIONES) {
            int i = rnd.nextInt(VENTAS);
            VentaGen v = gen.get(i);
            LineaGen l = v.lineas().get(rnd.nextInt(v.lineas().size()));
            if (!lineasDevueltas.add(i + ":" + l.varianteIdx())) {
                continue;
            }
            LocalDateTime fecha = v.fecha().plusDays(1 + rnd.nextInt(15));
            if (fecha.isAfter(tope)) {
                fecha = tope;
            }
            boolean apta = rnd.nextInt(10) < 7;
            filasDev.add(new Object[] {detalleIds.get(ventaIds.get(i) + ":" + varianteIds.get(l.varianteIdx())), 1,
                    "Devolución de rendimiento", apta, fecha, vendedor});
            if (apta) {
                eventos.get(l.varianteIdx()).add(new Ev(fecha, 2, "DEVOLUCION", 1, null, "VENTA", ventaIds.get(i)));
            }
        }
        insertar("devolucion", "detalle_venta_id, cantidad, motivo, apta_para_venta, fecha, usuario_id", filasDev);

        // kardex coherente por variante: entrada inicial, entradas periódicas, ventas, anulaciones y devoluciones
        LocalDateTime inicio = primerDia.minusDays(1).atTime(7, 0);
        for (int v = 0; v < 300; v++) {
            BigDecimal costo = precioCompra.get(v / 6);
            List<Ev> ev = eventos.get(v);
            ev.add(new Ev(inicio, 0, "ENTRADA", 100, costo, null, null));
            for (LocalDateTime f = inicio.plusDays(1 + (v % 9)); f.isBefore(tope); f = f.plusDays(9)) {
                ev.add(new Ev(f, 0, "ENTRADA", 15, costo, null, null));
            }
        }
        for (int i = 0; i < gen.size(); i++) {
            VentaGen vg = gen.get(i);
            for (LineaGen l : vg.lineas()) {
                eventos.get(l.varianteIdx()).add(new Ev(vg.fecha(), 1, "VENTA", l.cantidad(), null, "VENTA",
                        ventaIds.get(i)));
                if (vg.anulada()) {
                    eventos.get(l.varianteIdx()).add(new Ev(vg.fecha().plusHours(1), 1, "ANULACION_VENTA",
                            l.cantidad(), null, "VENTA", ventaIds.get(i)));
                }
            }
        }
        List<Object[]> filasMov = new ArrayList<>();
        int[] fisicoFinal = new int[300];
        for (int v = 0; v < 300; v++) {
            List<Ev> ev = eventos.get(v);
            ev.sort(Comparator.comparing(Ev::fecha).thenComparingInt(Ev::orden));
            int fisico = 0;
            for (Ev e : ev) {
                int despues = switch (e.tipo()) {
                    case "ENTRADA", "DEVOLUCION", "ANULACION_VENTA" -> fisico + e.cantidad();
                    default -> fisico - e.cantidad();
                };
                assertThat(despues).as("stock coherente de la variante %d", v).isGreaterThanOrEqualTo(0);
                filasMov.add(new Object[] {varianteIds.get(v), e.tipo(), e.cantidad(), fisico, despues, 0, 0,
                        e.costo(), vendedor, e.fecha(), e.refTipo(), e.refId(), null});
                fisico = despues;
            }
            fisicoFinal[v] = fisico;
        }
        // reservas: 60 ACTIVAS (con su movimiento RESERVA) y 20 VENCIDAS (cerradas por el sistema)
        LocalDateTime ahora = hoy.atStartOfDay().plusSeconds(1);
        List<Object[]> filasReserva = new ArrayList<>();
        int[] reservado = new int[300];
        for (int k = 0; k < 60; k++) {
            int v = k * 5;
            reservado[v] = 2;
            filasMov.add(new Object[] {varianteIds.get(v), "RESERVA", 2, fisicoFinal[v], fisicoFinal[v], 0, 2, null,
                    vendedor, ahora, null, null, null});
            filasReserva.add(new Object[] {varianteIds.get(v), 2, "Cliente T1", "@t1", ahora, ahora.plusHours(48),
                    "ACTIVA", null, vendedor, null, null, null});
        }
        for (int k = 0; k < 20; k++) {
            filasReserva.add(new Object[] {varianteIds.get(k), 1, "Cliente T1", "@t1", ahora.minusDays(10 + k),
                    ahora.minusDays(8 + k), "VENCIDA", null, vendedor, ahora.minusDays(8 + k), null, null});
        }
        insertar("movimiento", "variante_id, tipo, cantidad, fisico_antes, fisico_despues, reservado_antes,"
                + " reservado_despues, costo_unitario, usuario_id, fecha, referencia_tipo, referencia_id,"
                + " observacion", filasMov);
        insertar("reserva", "variante_id, cantidad, cliente_nombre, cliente_contacto, creada_en, vence_en, estado,"
                + " venta_id, usuario_id, cerrada_en, cerrada_por, motivo_cancelacion", filasReserva);
        List<Object[]> stock = new ArrayList<>();
        for (int v = 0; v < 300; v++) {
            stock.add(new Object[] {fisicoFinal[v], reservado[v], varianteIds.get(v)});
        }
        jdbc.batchUpdate("UPDATE variante SET stock_fisico = ?, stock_reservado = ? WHERE id = ?", stock);

        // planes de compra: 2 BORRADOR, 3 APROBADO (en camino), 5 RECIBIDO, con 20 líneas cada uno
        String[] estados = {"BORRADOR", "BORRADOR", "APROBADO", "APROBADO", "APROBADO", "RECIBIDO", "RECIBIDO",
            "RECIBIDO", "RECIBIDO", "RECIBIDO"};
        for (int k = 0; k < estados.length; k++) {
            String e = estados[k];
            LocalDateTime t = ahora.minusDays(20 + k);
            jdbc.update("INSERT INTO plan_compra (estado, origen, observacion, creado_en, creado_por, aprobado_en,"
                    + " aprobado_por, recibido_en, recibido_por, version, actualizado_en)"
                    + " VALUES (?, 'MANUAL', ?, ?, ?, ?, ?, ?, ?, 0, ?)", e, "T1-" + sufijo, t, admin,
                    e.equals("BORRADOR") ? null : t, e.equals("BORRADOR") ? null : admin,
                    e.equals("RECIBIDO") ? t.plusDays(5) : null, e.equals("RECIBIDO") ? admin : null, t);
        }
        planes.addAll(jdbc.queryForList("SELECT id FROM plan_compra WHERE observacion = ? ORDER BY id", Long.class,
                "T1-" + sufijo));
        List<Object[]> filasPlan = new ArrayList<>();
        for (int k = 0; k < planes.size(); k++) {
            boolean recibido = estados[k].equals("RECIBIDO");
            for (int j = 0; j < 20; j++) {
                filasPlan.add(new Object[] {planes.get(k), varianteIds.get(k * 20 + j), 50, 50,
                        new BigDecimal("12000.00"), "Prueba", recibido ? 50 : null,
                        recibido ? new BigDecimal("12000.00") : null});
            }
        }
        insertar("detalle_plan", "plan_id, variante_id, cantidad_sugerida, cantidad_aprobada, costo_estimado,"
                + " justificacion, cantidad_recibida, costo_recibido", filasPlan);

        // usuarios por rol para el JWT
        for (Rol rol : List.of(Rol.INVENTARIO, Rol.CONTABILIDAD)) {
            String email = rol.name().toLowerCase() + ".rend." + sufijo + "@livorno.test";
            usuariosExtra.add(usuarios.save(new Usuario("Rend " + rol, email, encoder.encode(CLAVE), rol)).getId());
        }
        for (String tabla : List.of("venta", "detalle_venta", "devolucion", "movimiento", "variante", "producto",
                "categoria", "reserva", "plan_compra", "detalle_plan")) {
            jdbc.queryForList("ANALYZE TABLE " + tabla);
        }
        Long ventasTotales = jdbc.queryForObject("SELECT COUNT(*) FROM venta WHERE usuario_id = ?", Long.class,
                vendedor);
        Long movs = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento", Long.class);
        System.out.printf("[RNF-03] datos: %d ventas, %d líneas, %d devoluciones, %d movimientos%n", ventasTotales,
                filasDetalle.size(), filasDev.size(), movs);
        tokens.put("ADMIN", login("admin.prueba@livorno.test", "ClaveAdminPrueba123!"));
        tokens.put("CONTABILIDAD", login("contabilidad.rend." + sufijo + "@livorno.test", CLAVE));
        tokens.put("INVENTARIO", login("inventario.rend." + sufijo + "@livorno.test", CLAVE));
        // el VENDEDOR del helper no tiene contraseña real: se crea otro para el panel
        usuariosExtra.add(usuarios.save(new Usuario("Rend VENDEDOR", "vendedor.rend." + sufijo + "@livorno.test",
                encoder.encode(CLAVE), Rol.VENDEDOR)).getId());
        tokens.put("VENDEDOR", login("vendedor.rend." + sufijo + "@livorno.test", CLAVE));
    }

    @AfterAll
    void limpiar() {
        try {
            if (!planes.isEmpty()) {
                jdbc.update("DELETE FROM detalle_plan WHERE plan_id IN (" + csv(planes) + ")");
                jdbc.update("DELETE FROM plan_compra WHERE id IN (" + csv(planes) + ")");
            }
            if (datos != null) {
                datos.limpiar();
            }
        } finally {
            for (Long id : usuariosExtra) {
                usuarios.deleteById(id);
            }
        }
    }

    private void insertar(String tabla, String columnas, List<Object[]> filas) {
        int n = filas.get(0).length;
        String ph = "(" + "?,".repeat(n - 1) + "?)";
        for (int i = 0; i < filas.size(); i += 500) {
            List<Object[]> lote = filas.subList(i, Math.min(i + 500, filas.size()));
            StringBuilder sql = new StringBuilder("INSERT INTO " + tabla + " (" + columnas + ") VALUES ");
            List<Object> args = new ArrayList<>();
            for (int j = 0; j < lote.size(); j++) {
                sql.append(j == 0 ? ph : "," + ph);
                args.addAll(java.util.Arrays.asList(lote.get(j)));
            }
            jdbc.update(sql.toString(), args.toArray());
        }
    }

    private static String csv(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("NULL");
    }

    private String login(String email, String clave) {
        try {
            limitador.limpiar();
            String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + clave + "\"}"))
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(cuerpo, "$.token");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar sesión como " + email, e);
        }
    }

    // ---------- medición ----------

    private record Medida(String nombre, long peorMs, long medianaMs) {}

    private interface Peticion {
        MvcResult ejecutar() throws Exception;
    }

    private MvcResult pedir(String ruta, String rol) throws Exception {
        MvcResult r = mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(rol)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("%s como %s", ruta, rol).isEqualTo(200);
        return r;
    }

    private Medida medir(String nombre, String ruta, String rol) throws Exception {
        return medir(nombre, () -> pedir(ruta, rol));
    }

    private Medida medir(String nombre, Peticion p) throws Exception {
        p.ejecutar(); // calentamiento
        long[] ms = new long[CORRIDAS];
        for (int i = 0; i < CORRIDAS; i++) {
            long t0 = System.nanoTime();
            MvcResult r = p.ejecutar();
            ms[i] = (System.nanoTime() - t0) / 1_000_000;
            assertThat(r.getResponse().getContentAsByteArray().length).as(nombre).isPositive();
        }
        java.util.Arrays.sort(ms);
        return new Medida(nombre, ms[CORRIDAS - 1], ms[CORRIDAS / 2]);
    }

    private String rango(boolean veinticuatro) {
        return "desde=" + hoy.minusDays(veinticuatro ? 729 : 364) + "&hasta=" + hoy;
    }

    @Test
    void reportesYConsultas_conDoceMilVentas_respondenEnMenosDeTresSegundos() throws Exception {
        List<Medida> m = new ArrayList<>();
        for (boolean largo : new boolean[] {false, true}) {
            String meses = largo ? "24m" : "12m";
            for (String agr : List.of("CATEGORIA", "PRODUCTO", "VARIANTE")) {
                m.add(medir("rentabilidad " + meses + " " + agr,
                        "/api/reportes/rentabilidad?" + rango(largo) + "&agrupacion=" + agr, "ADMIN"));
            }
        }
        m.add(medir("libro DIA 12m", "/api/reportes/libro-ventas?" + rango(false) + "&agrupacion=DIA", "CONTABILIDAD"));
        m.add(medir("libro MES 24m", "/api/reportes/libro-ventas?" + rango(true) + "&agrupacion=MES", "CONTABILIDAD"));
        m.add(medir("valorizacion VARIANTE", "/api/reportes/valorizacion?agrupacion=VARIANTE", "ADMIN"));
        m.add(medir("valorizacion PRODUCTO", "/api/reportes/valorizacion?agrupacion=PRODUCTO", "ADMIN"));
        m.add(medir("movimientos 12m PRODUCTO", "/api/reportes/movimientos?" + rango(false) + "&agrupacion=PRODUCTO",
                "CONTABILIDAD"));
        m.add(medir("movimientos 24m VARIANTE", "/api/reportes/movimientos?" + rango(true) + "&agrupacion=VARIANTE",
                "ADMIN"));
        for (String f : List.of("PDF", "XLSX")) {
            m.add(medir("exportar rentabilidad 24m VARIANTE " + f, "/api/reportes/rentabilidad/exportar?"
                    + rango(true) + "&agrupacion=VARIANTE&formato=" + f, "ADMIN"));
            m.add(medir("exportar libro DIA 24m " + f, "/api/reportes/libro-ventas/exportar?" + rango(true)
                    + "&agrupacion=DIA&formato=" + f, "CONTABILIDAD"));
            m.add(medir("exportar valorizacion VARIANTE " + f, "/api/reportes/valorizacion/exportar?"
                    + "agrupacion=VARIANTE&formato=" + f, "ADMIN"));
            m.add(medir("exportar movimientos 12m VARIANTE " + f, "/api/reportes/movimientos/exportar?"
                    + rango(false) + "&agrupacion=VARIANTE&formato=" + f, "CONTABILIDAD"));
            m.add(medir("exportar tendencias " + f, "/api/analitica/tendencias/exportar?formato=" + f, "INVENTARIO"));
        }
        for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
            m.add(medir("panel " + rol, "/api/panel", rol));
        }
        m.add(medir("tendencias 24m", "/api/analitica/tendencias", "ADMIN"));
        m.add(medir("mas vendidos 24m VARIANTE", "/api/analitica/mas-vendidos?" + rango(true)
                + "&agrupacion=VARIANTE&limite=50", "VENDEDOR"));
        m.add(medir("kardex de una variante", "/api/movimientos?varianteId=" + varianteKardex + "&size=20", "ADMIN"));
        m.add(medir("kardex global por fechas", "/api/movimientos?" + rango(false) + "&size=20", "ADMIN"));
        m.add(medir("listado de inventario", "/api/inventario?size=20", "VENDEDOR"));
        m.add(medir("listado de inventario con busqueda", "/api/inventario?q=RD1&size=20", "VENDEDOR"));
        m.add(medir("listado de ventas", "/api/ventas?size=20", "ADMIN"));
        m.add(medir("listado de ventas 12m", "/api/ventas?" + rango(false) + "&size=20", "ADMIN"));

        StringBuilder tabla = new StringBuilder("\n[RNF-03] Tiempos (ms) con ~12.000 ventas: peor de " + CORRIDAS
                + " corridas tras 1 de calentamiento\n");
        tabla.append(String.format("%-52s %8s %8s%n", "Consulta", "Peor", "Mediana"));
        List<String> lentas = new ArrayList<>();
        for (Medida x : m) {
            tabla.append(String.format("%-52s %8d %8d%s%n", x.nombre(), x.peorMs(), x.medianaMs(),
                    x.peorMs() >= LIMITE_MS ? "  <-- LENTA" : ""));
            if (x.peorMs() >= LIMITE_MS) {
                lentas.add(x.nombre() + " = " + x.peorMs() + " ms");
            }
        }
        System.out.println(tabla);
        assertThat(lentas).as("consultas de 3 s o más").isEmpty();
    }

    // ---------- EXPLAIN ----------

    private static final Pattern TABLA_ALIAS = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+`?(venta|detalle_venta|movimiento|devolucion)`?(?:\\s+(?:as\\s+)?`?(\\w+)`?)?");
    private static final Set<String> PALABRAS = Set.of("join", "where", "on", "left", "right", "inner", "outer",
            "cross", "group", "order", "limit", "using", "straight_join", "union", "having", "for", "lock");

    private record Plan(String sql, String tabla, String alias, String tipo, String clave, String filas) {}

    private List<String> capturar(Peticiones peticiones) throws Exception {
        String url = contenedor.getJdbcUrl();
        try (Connection c = DriverManager.getConnection(url, "root", contenedor.getPassword());
                Statement st = c.createStatement()) {
            st.execute("SET GLOBAL general_log = 'OFF'");
            st.execute("SET GLOBAL log_output = 'TABLE'");
            st.execute("TRUNCATE TABLE mysql.general_log");
            st.execute("SET GLOBAL general_log = 'ON'");
            try {
                peticiones.ejecutar();
            } finally {
                st.execute("SET GLOBAL general_log = 'OFF'");
            }
            List<String> sqls = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT CONVERT(argument USING utf8mb4) FROM mysql.general_log"
                    + " WHERE command_type = 'Query' ORDER BY event_time")) {
                while (rs.next()) {
                    String s = rs.getString(1);
                    if (s != null && s.stripLeading().regionMatches(true, 0, "select", 0, 6)
                            && TABLA_ALIAS.matcher(s).find()) {
                        sqls.add(s.strip());
                    }
                }
            }
            return sqls;
        }
    }

    private interface Peticiones {
        void ejecutar() throws Exception;
    }

    /** EXPLAIN de cada consulta distinta; devuelve una fila por acceso a venta, detalle_venta, movimiento o devolucion. */
    private List<Plan> explicar(List<String> sqls) {
        List<Plan> planes = new ArrayList<>();
        for (String sql : new java.util.LinkedHashSet<>(sqls)) {
            Map<String, String> aliasATabla = new HashMap<>();
            Matcher mt = TABLA_ALIAS.matcher(sql);
            while (mt.find()) {
                String tabla = mt.group(1).toLowerCase();
                aliasATabla.put(tabla, tabla);
                if (mt.group(2) != null && !PALABRAS.contains(mt.group(2).toLowerCase())) {
                    aliasATabla.put(mt.group(2), tabla);
                }
            }
            for (Map<String, Object> fila : jdbc.queryForList("EXPLAIN " + sql)) {
                String nombre = String.valueOf(fila.get("table"));
                String tabla = aliasATabla.get(nombre);
                if (tabla != null) {
                    planes.add(new Plan(sql, tabla, nombre, String.valueOf(fila.get("type")),
                            String.valueOf(fila.get("key")), String.valueOf(fila.get("rows"))));
                }
            }
        }
        return planes;
    }

    private static String corto(String sql) {
        String s = sql.replaceAll("\\s+", " ");
        return s.length() > 150 ? s.substring(0, 150) + "..." : s;
    }

    @Test
    void consultas_enRangoAcotado_noRecorrenPorCompletoVentaDetalleNiMovimiento() throws Exception {
        String angosto = "desde=" + hoy.minusDays(60) + "&hasta=" + hoy.minusDays(30);
        List<String> sqls = capturar(() -> {
            for (String agr : List.of("CATEGORIA", "PRODUCTO", "VARIANTE")) {
                pedir("/api/reportes/rentabilidad?" + angosto + "&agrupacion=" + agr, "ADMIN");
                pedir("/api/reportes/valorizacion?agrupacion=" + agr, "ADMIN");
            }
            Long categoria = jdbc.queryForObject("SELECT MIN(id) FROM categoria WHERE nombre LIKE 'Rend0%'",
                    Long.class);
            pedir("/api/reportes/rentabilidad?" + angosto + "&agrupacion=PRODUCTO&categoriaId=" + categoria, "ADMIN");
            pedir("/api/reportes/libro-ventas?" + angosto + "&agrupacion=DIA", "ADMIN");
            pedir("/api/reportes/libro-ventas?" + angosto + "&agrupacion=MES", "ADMIN");
            pedir("/api/reportes/libro-ventas/exportar?" + angosto + "&formato=XLSX", "ADMIN");
            pedir("/api/reportes/libro-ventas/exportar?" + angosto + "&formato=PDF", "ADMIN");
            pedir("/api/reportes/movimientos?" + angosto + "&agrupacion=PRODUCTO", "ADMIN");
            pedir("/api/reportes/movimientos/exportar?" + angosto + "&formato=XLSX", "ADMIN");
            for (String rol : List.of("ADMIN", "INVENTARIO", "VENDEDOR", "CONTABILIDAD")) {
                pedir("/api/panel", rol);
            }
            pedir("/api/analitica/tendencias", "ADMIN");
            pedir("/api/analitica/mas-vendidos?" + angosto + "&agrupacion=VARIANTE", "ADMIN");
            pedir("/api/movimientos?varianteId=" + varianteKardex + "&size=20", "ADMIN");
            pedir("/api/movimientos?" + angosto + "&size=20", "ADMIN");
            pedir("/api/inventario?size=20", "ADMIN");
            pedir("/api/ventas?size=20", "ADMIN");
            pedir("/api/ventas?" + angosto + "&size=20", "ADMIN");
        });
        assertThat(sqls).as("consultas capturadas del log general de MySQL").isNotEmpty();

        List<Plan> plan = explicar(sqls);
        StringBuilder log = new StringBuilder("\n[RNF-03] EXPLAIN (rango acotado de 30 días, "
                + new HashSet<>(sqls).size() + " consultas distintas)\n");
        List<String> completos = new ArrayList<>();
        for (Plan p : plan) {
            log.append(String.format("  %-14s alias=%-8s type=%-6s key=%-32s rows=%-8s %s%n", p.tabla(), p.alias(),
                    p.tipo(), p.clave(), p.filas(), corto(p.sql())));
            if (p.tipo().equals("ALL") && Set.of("venta", "detalle_venta", "movimiento").contains(p.tabla())) {
                completos.add(p.tabla() + " (" + p.alias() + ") ALL en: " + corto(p.sql()));
            }
        }
        System.out.println(log);
        assertThat(completos).as("accesos type = ALL sobre tablas grandes").isEmpty();
    }

    @Test
    void consultas_enRangoDe24Meses_planInformativo() throws Exception {
        List<String> sqls = capturar(() -> {
            pedir("/api/reportes/rentabilidad?" + rango(true) + "&agrupacion=VARIANTE", "ADMIN");
            pedir("/api/reportes/libro-ventas?" + rango(true) + "&agrupacion=DIA", "ADMIN");
            pedir("/api/reportes/libro-ventas/exportar?" + rango(true) + "&formato=XLSX", "ADMIN");
        });
        StringBuilder log = new StringBuilder("\n[RNF-03] EXPLAIN informativo (rango de 24 meses, no se afirma)\n");
        for (Plan p : explicar(sqls)) {
            log.append(String.format("  %-14s alias=%-8s type=%-6s key=%-32s rows=%-8s %s%n", p.tabla(), p.alias(),
                    p.tipo(), p.clave(), p.filas(), corto(p.sql())));
        }
        System.out.println(log);
        assertThat(sqls).isNotEmpty();
    }
}
