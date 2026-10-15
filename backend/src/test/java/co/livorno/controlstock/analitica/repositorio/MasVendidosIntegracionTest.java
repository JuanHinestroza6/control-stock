package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.MasVendidoItem;
import co.livorno.controlstock.analitica.dto.MasVendidosResponse;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ranking de más vendidos contra MySQL real (B6, fase 5). Rango 2019-05-01..2019-05-10 y una categoría propia para
 * aislar los datos de otras pruebas.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class MasVendidosIntegracionTest {

    static final LocalDate DESDE = LocalDate.of(2019, 5, 1);
    static final LocalDate HASTA = LocalDate.of(2019, 5, 10);

    @Autowired MasVendidosService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultasVentaAnalitica consultas;

    String sufijo;
    Long usuarioId;
    Long categoriaId;
    Long categoriaOtraId;
    Long producto1;
    Long producto2;
    Long v1a;
    Long v1b;
    Long v1c;
    Long v2;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "mv" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Ranking', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        categoriaId = categoria("MV1" + sufijo);
        categoriaOtraId = categoria("MV2" + sufijo);
        producto1 = producto("MA" + sufijo, true);
        producto2 = producto("MB" + sufijo, false);
        v1a = variante(producto1, "MA" + sufijo + "-M-NEG", "M", true);
        v1b = variante(producto1, "MA" + sufijo + "-L-NEG", "L", true);
        v1c = variante(producto1, "MA" + sufijo + "-S-NEG", "S", true);
        v2 = variante(producto2, "MB" + sufijo + "-M-NEG", "M", false);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM reserva WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN (?, ?, ?, ?)", v1a, v1b, v1c, v2);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM alerta WHERE variante_id IN (?, ?, ?, ?)", v1a, v1b, v1c, v2);
        jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN (?, ?, ?, ?)", v1a, v1b, v1c, v2);
        jdbc.update("DELETE FROM variante WHERE producto_id IN (?, ?)", producto1, producto2);
        jdbc.update("DELETE FROM producto WHERE id IN (?, ?)", producto1, producto2);
        jdbc.update("DELETE FROM categoria WHERE id IN (?, ?)", categoriaId, categoriaOtraId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long categoria(String nombre) {
        jdbc.update("INSERT INTO categoria (nombre, creado_en, actualizado_en) VALUES (?, NOW(6), NOW(6))", nombre);
        return jdbc.queryForObject("SELECT id FROM categoria WHERE nombre = ?", Long.class, nombre);
    }

    private Long producto(String codigo, boolean activo) {
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 10.00, 100.00, ?, NOW(6), NOW(6))",
                codigo, "Prod " + codigo, categoriaId, activo);
        return jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, codigo);
    }

    private Long variante(Long productoId, String sku, String talla, boolean activo) {
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, ?, NOW(6), NOW(6))",
                productoId, sku, talla, activo);
        return jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
    }

    private Long venta(LocalDateTime fecha, boolean anulada) {
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado, anulada_en, anulada_por, motivo_anulacion)"
                + " VALUES (?, ?, 0, ?, ?, ?, ?)", fecha, usuarioId, anulada ? "ANULADA" : "REGISTRADA",
                anulada ? fecha.plusHours(1) : null, anulada ? usuarioId : null, anulada ? "prueba" : null);
        return jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
    }

    private Long linea(Long ventaId, Long varianteId, int cantidad, String precioUnitario) {
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, ?, 10.00)", ventaId, varianteId, cantidad,
                new BigDecimal(precioUnitario));
        return jdbc.queryForObject("SELECT id FROM detalle_venta WHERE venta_id = ? AND variante_id = ?", Long.class,
                ventaId, varianteId);
    }

    private void devolucion(Long detalleId, int cantidad, LocalDateTime fecha) {
        jdbc.update("INSERT INTO devolucion (detalle_venta_id, cantidad, motivo, apta_para_venta, fecha, usuario_id)"
                + " VALUES (?, ?, 'prueba', FALSE, ?, ?)", detalleId, cantidad, fecha, usuarioId);
    }

    /**
     * Neto esperado: v1a 2 u / 200 (3 vendidas, 1 devuelta), v1b 2 u / 200 (último día 23:59), v2 4 u / 200, v1c 0
     * (todo devuelto). Quedan fuera: venta anulada, venta del 11 a las 00:00 y del 30 de abril a las 23:59.
     */
    private void escenario() {
        Long s1 = venta(LocalDateTime.of(2019, 5, 1, 0, 0), false);
        Long detalleA = linea(s1, v1a, 3, "100.00");
        linea(s1, v2, 4, "50.00");
        devolucion(detalleA, 1, LocalDateTime.of(2019, 6, 20, 9, 0));
        Long s2 = venta(LocalDateTime.of(2019, 5, 10, 23, 59), false);
        linea(s2, v1b, 2, "100.00");
        Long s3 = venta(LocalDateTime.of(2019, 5, 3, 12, 0), false);
        Long detalleC = linea(s3, v1c, 2, "100.00");
        devolucion(detalleC, 2, LocalDateTime.of(2019, 5, 4, 12, 0));
        Long fuera1 = venta(LocalDateTime.of(2019, 5, 11, 0, 0), false);
        linea(fuera1, v1a, 100, "100.00");
        Long fuera2 = venta(LocalDateTime.of(2019, 4, 30, 23, 59), false);
        linea(fuera2, v1a, 100, "100.00");
        Long anulada = venta(LocalDateTime.of(2019, 5, 5, 10, 0), true);
        linea(anulada, v1a, 50, "100.00");
    }

    private MasVendidosResponse consultar(Long categoria, CriterioRanking criterio, AgrupacionRanking agrupacion,
            Integer limite) {
        return servicio.consultar(DESDE, HASTA, categoria, criterio, agrupacion, limite);
    }

    @Test
    void masVendidos_porProductoYUnidades_netoConEmpateDesempatadoPorIngreso() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.PRODUCTO, null);

        // P1 = 4 u / 400 (v1a 2 + v1b 2); P2 = 4 u / 200; empate en unidades, gana el de mayor ingreso
        assertThat(r.items()).extracting(MasVendidoItem::productoId).containsExactly(producto1, producto2);
        assertThat(r.items().get(0).unidades()).isEqualTo(4);
        assertThat(r.items().get(0).ingreso()).isEqualByComparingTo("400.00");
        assertThat(r.items().get(0).posicion()).isEqualTo(1);
        assertThat(r.items().get(0).varianteId()).isNull();
        assertThat(r.items().get(0).categoria().id()).isEqualTo(categoriaId);
        assertThat(r.totalUnidades()).isEqualTo(8);
        assertThat(r.totalIngreso()).isEqualByComparingTo("600.00");
        assertThat(r.items().get(0).participacion()).isEqualByComparingTo("50.0");
        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.hasta()).isEqualTo(HASTA);
    }

    @Test
    void masVendidos_porProductoEIngreso_participacionSobreElTotalDelIngreso() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.INGRESO, AgrupacionRanking.PRODUCTO, null);

        assertThat(r.items()).extracting(MasVendidoItem::productoId).containsExactly(producto1, producto2);
        assertThat(r.items().get(0).participacion()).isEqualByComparingTo("66.7");
        assertThat(r.items().get(1).participacion()).isEqualByComparingTo("33.3");
    }

    @Test
    void masVendidos_porVarianteEIngreso_empateDesempatadoPorUnidadesYLuegoId() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.INGRESO, AgrupacionRanking.VARIANTE, null);

        // las tres con ingreso 200: v2 (4 u) primero; v1a y v1b (2 u) por id ascendente. v1c (neto 0) no aparece
        assertThat(r.items()).extracting(MasVendidoItem::varianteId).containsExactly(v2, v1a, v1b);
        assertThat(r.items().get(0).sku()).isEqualTo("MB" + sufijo + "-M-NEG");
        assertThat(r.items().get(0).color()).isEqualTo("NEGRO");
        assertThat(r.items().get(1).unidades()).isEqualTo(2);
    }

    @Test
    void masVendidos_porVarianteYUnidades_ordenaPorUnidadesDescendente() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.VARIANTE, 2);

        assertThat(r.items()).extracting(MasVendidoItem::varianteId).containsExactly(v2, v1a);
        // los totales son de todo el periodo, no solo del top
        assertThat(r.totalUnidades()).isEqualTo(8);
    }

    @Test
    void masVendidos_inactivos_seIncluyenConMarca() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.PRODUCTO, null);

        assertThat(r.items().get(0).activo()).isTrue();
        assertThat(r.items().get(1).activo()).isFalse();
    }

    @Test
    void masVendidos_rangoInclusivo_incluyeLaVentaDelUltimoDiaALas2359() {
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.VARIANTE, null);

        assertThat(r.items()).extracting(MasVendidoItem::varianteId).contains(v1b);
        assertThat(r.items()).extracting(MasVendidoItem::varianteId).doesNotContain(v1c);
        // la venta del 11 de mayo 00:00 (100 u de v1a) y la anulada (50 u) no cuentan
        assertThat(r.items().stream().filter(i -> i.varianteId().equals(v1a)).findFirst().orElseThrow().unidades())
                .isEqualTo(2);
    }

    @Test
    void masVendidos_categoriaSinVentas_listaVaciaYTotalesCero() {
        escenario();

        MasVendidosResponse r = consultar(categoriaOtraId, CriterioRanking.UNIDADES, AgrupacionRanking.PRODUCTO, null);

        assertThat(r.items()).isEmpty();
        assertThat(r.totalUnidades()).isZero();
        assertThat(r.totalIngreso()).isEqualByComparingTo("0.00");
    }

    @Test
    void masVendidos_categoriaInexistente_listaVaciaSinError() {
        escenario();

        MasVendidosResponse r = consultar(-1L, CriterioRanking.UNIDADES, AgrupacionRanking.PRODUCTO, null);

        assertThat(r.items()).isEmpty();
        assertThat(r.totalUnidades()).isZero();
    }

    @Test
    void masVendidos_ventaAnuladaUnica_noApareceNiSuma() {
        Long anulada = venta(LocalDateTime.of(2019, 5, 5, 10, 0), true);
        linea(anulada, v1a, 50, "100.00");

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.PRODUCTO, null);

        assertThat(r.items()).isEmpty();
        assertThat(r.totalIngreso()).isEqualByComparingTo("0.00");
        assertThat(r.items()).extracting(MasVendidoItem::productoId).isEqualTo(List.of());
    }

    @Test
    void masVendidos_variasDevolucionesDeUnaLinea_noDuplicanFilasNiRestanDeMas() {
        // dado: una línea de 10 u con dos devoluciones (3 + 2) y otra línea de la misma venta
        Long s = venta(LocalDateTime.of(2019, 5, 2, 10, 0), false);
        Long detalle = linea(s, v1a, 10, "100.00");
        linea(s, v1b, 4, "100.00");
        devolucion(detalle, 3, LocalDateTime.of(2019, 5, 3, 10, 0));
        devolucion(detalle, 2, LocalDateTime.of(2019, 5, 4, 10, 0));

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.UNIDADES, AgrupacionRanking.VARIANTE, null);

        // v1a: 10 - 5 = 5 u / 500; v1b: 4 u / 400 (sin duplicar por el join de devoluciones)
        assertThat(r.items()).extracting(MasVendidoItem::varianteId).containsExactly(v1a, v1b);
        assertThat(r.items().get(0).unidades()).isEqualTo(5);
        assertThat(r.items().get(0).ingreso()).isEqualByComparingTo("500.00");
        assertThat(r.items().get(1).unidades()).isEqualTo(4);
        assertThat(r.totalUnidades()).isEqualTo(9);
        assertThat(r.totalIngreso()).isEqualByComparingTo("900.00");
    }

    @Test
    void masVendidos_ingresoPorVariante_coincideConIngresoNetoPorVarianteDeLaAbc() {
        // dado: el escenario completo, con devoluciones, anulada y ventas fuera de rango
        escenario();

        MasVendidosResponse r = consultar(categoriaId, CriterioRanking.INGRESO, AgrupacionRanking.VARIANTE, 50);
        List<VentaNetaVariante> abc = consultas.ingresoNetoPorVariante(DESDE, HASTA.plusDays(1)).stream()
                .filter(v -> v.varianteId() == v1a || v.varianteId() == v1b || v.varianteId() == v1c
                        || v.varianteId() == v2).toList();

        // entonces: mismas variantes, unidades e ingreso (v1c con neto 0 puede faltar en el ranking)
        for (MasVendidoItem item : r.items()) {
            VentaNetaVariante ref = abc.stream().filter(v -> v.varianteId() == item.varianteId()).findFirst()
                    .orElseThrow();
            assertThat(item.unidades()).isEqualTo(ref.unidadesNetas());
            assertThat(item.ingreso()).isEqualByComparingTo(ref.ingresoNeto());
        }
        assertThat(abc.stream().filter(v -> v.unidadesNetas() > 0).count()).isEqualTo(r.items().size());
        assertThat(r.totalIngreso()).isEqualByComparingTo(
                abc.stream().map(VentaNetaVariante::ingresoNeto).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void masVendidos_dtoNoExponeCampos_deCosto() {
        // el VENDEDOR recibe este mismo DTO: no debe tener campos de costo ni margen
        assertThat(MasVendidoItem.class.getRecordComponents())
                .extracting(c -> c.getName().toLowerCase())
                .noneMatch(n -> n.contains("costo") || n.contains("margen") || n.contains("compra"));
    }
}
