package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
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
 * Consultas de venta neta e ingreso para la analítica contra MySQL real (B2, fase 5). Los datos se arman con SQL
 * porque las fechas deben ser exactas y el stock no interviene.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class ConsultasVentaAnaliticaIntegracionTest {

    static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 3, 11);

    @Autowired ConsultasVentaAnalitica consultas;
    @Autowired JdbcTemplate jdbc;

    Long usuarioId;
    Long productoId;
    Long variante1;
    Long variante2;

    @BeforeEach
    void preparar() {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "cva" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Consultas', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, NOW(6), NOW(6))", "C" + sufijo, categoria);
        productoId = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "C" + sufijo);
        variante1 = crearVariante("C" + sufijo + "-M-NEG", "M");
        variante2 = crearVariante("C" + sufijo + "-L-NEG", "L");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM reserva WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM detalle_venta WHERE variante_id IN (?, ?)", variante1, variante2);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM alerta WHERE variante_id IN (?, ?)", variante1, variante2);
        jdbc.update("DELETE FROM analitica_variante WHERE variante_id IN (?, ?)", variante1, variante2);
        jdbc.update("DELETE FROM variante WHERE producto_id = ?", productoId);
        jdbc.update("DELETE FROM producto WHERE id = ?", productoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long crearVariante(String sku, String talla) {
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, ?, 'NEGRO', 0, 0, NOW(6), NOW(6))", productoId, sku, talla);
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

    private void devolucion(Long detalleId, int cantidad, boolean apta, LocalDateTime fecha) {
        jdbc.update("INSERT INTO devolucion (detalle_venta_id, cantidad, motivo, apta_para_venta, fecha, usuario_id)"
                + " VALUES (?, ?, 'prueba', ?, ?, ?)", detalleId, cantidad, apta, fecha, usuarioId);
    }

    private List<VentaNetaVariante> ingreso() {
        return consultas.ingresoNetoPorVariante(DESDE, HASTA).stream()
                .filter(v -> v.varianteId() == variante1 || v.varianteId() == variante2).toList();
    }

    private List<DemandaDiaVariante> demanda() {
        return consultas.demandaDiariaNeta(DESDE, HASTA).stream()
                .filter(d -> d.varianteId() == variante1 || d.varianteId() == variante2).toList();
    }

    @Test
    void ingresoNeto_variasLineasYVariantes_sumaPorVariante() {
        // dado: dos ventas con dos variantes, una con descuento
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        linea(v1, variante1, 2, "100.00");
        linea(v1, variante2, 1, "80.00");
        Long v2 = venta(LocalDateTime.of(2026, 3, 5, 15, 30), false);
        linea(v2, variante1, 3, "90.50");

        // cuando / entonces: unidades e ingreso (BigDecimal escala 2) por variante, ordenado por id
        assertThat(ingreso()).containsExactly(
                new VentaNetaVariante(variante1, 5, new BigDecimal("471.50")),
                new VentaNetaVariante(variante2, 1, new BigDecimal("80.00")));
        assertThat(ingreso().get(0).aIngreso().ingresoNeto()).isEqualByComparingTo("471.50");
    }

    @Test
    void ingresoNeto_ventaAnulada_seExcluye() {
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        linea(v1, variante1, 2, "100.00");
        Long anulada = venta(LocalDateTime.of(2026, 3, 3, 10, 0), true);
        linea(anulada, variante1, 7, "100.00");
        linea(anulada, variante2, 4, "100.00");

        assertThat(ingreso()).containsExactly(new VentaNetaVariante(variante1, 2, new BigDecimal("200.00")));
        assertThat(demanda()).containsExactly(new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 2), 2));
    }

    @Test
    void ingresoNeto_devolucionAptaYNoApta_ambasRestanEnLaFechaDeLaVenta() {
        // dado: venta del 2 de marzo; devolución apta el 4 y no apta fuera del rango (15 de marzo)
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        Long detalle = linea(v1, variante1, 10, "50.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 4, 9, 0));
        devolucion(detalle, 3, false, LocalDateTime.of(2026, 3, 15, 9, 0));

        // entonces: 10 - 2 - 3 = 5 unidades, 5 x 50 = 250, aunque una devolución sea posterior al rango
        assertThat(ingreso()).containsExactly(new VentaNetaVariante(variante1, 5, new BigDecimal("250.00")));
    }

    @Test
    void ingresoNeto_todoDevuelto_apareceConCero() {
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        Long detalle = linea(v1, variante1, 2, "50.00");
        devolucion(detalle, 2, false, LocalDateTime.of(2026, 3, 3, 9, 0));

        assertThat(ingreso()).containsExactly(new VentaNetaVariante(variante1, 0, new BigDecimal("0.00")));
    }

    @Test
    void demandaDiaria_devolucionAptaYNoApta_soloLaAptaRestaEnLaFechaDeLaDevolucion() {
        // dado: venta de 10 el día 2; devolución apta de 2 el día 4; no apta de 3 el día 5 (es consumo)
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        Long detalle = linea(v1, variante1, 10, "50.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 4, 9, 0));
        devolucion(detalle, 3, false, LocalDateTime.of(2026, 3, 5, 9, 0));

        // entonces: día 2 = +10; día 4 = -2 (fila negativa, el recorte lo hace SerieDemanda); la no apta no aparece
        assertThat(demanda()).containsExactly(
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 2), 10),
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 4), -2));
    }

    @Test
    void demandaDiaria_ventasDelMismoDiaYVariasVariantes_agrupaPorVarianteYDia() {
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 8, 0), false);
        linea(v1, variante1, 2, "100.00");
        linea(v1, variante2, 1, "100.00");
        Long v2 = venta(LocalDateTime.of(2026, 3, 2, 20, 0), false);
        linea(v2, variante1, 4, "100.00");
        Long v3 = venta(LocalDateTime.of(2026, 3, 3, 12, 0), false);
        linea(v3, variante1, 1, "100.00");

        assertThat(demanda()).containsExactly(
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 2), 6),
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 3), 1),
                new DemandaDiaVariante(variante2, LocalDate.of(2026, 3, 2), 1));
    }

    @Test
    void bordes_semiabierto_excluyeMedianocheDelHastaEIncluyeElUltimoSegundoYElDesde() {
        Long enDesde = venta(LocalDateTime.of(2026, 3, 1, 0, 0), false);
        linea(enDesde, variante1, 1, "100.00");
        Long ultimoSegundo = venta(LocalDateTime.of(2026, 3, 10, 23, 59, 59), false);
        linea(ultimoSegundo, variante1, 2, "100.00");
        Long enHasta = venta(LocalDateTime.of(2026, 3, 11, 0, 0), false);
        linea(enHasta, variante1, 4, "100.00");
        Long antes = venta(LocalDateTime.of(2026, 2, 28, 23, 59, 59), false);
        linea(antes, variante1, 8, "100.00");

        assertThat(ingreso()).containsExactly(new VentaNetaVariante(variante1, 3, new BigDecimal("300.00")));
        assertThat(demanda()).containsExactly(
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 1), 1),
                new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 10), 2));
    }

    @Test
    void bordes_devolucionEnElHasta_noRestaEnDemandaDeInventario() {
        // dado: venta el día 10 y devolución apta a las 00:00 del 11 (hasta, excluido)
        Long v1 = venta(LocalDateTime.of(2026, 3, 10, 10, 0), false);
        Long detalle = linea(v1, variante1, 3, "100.00");
        devolucion(detalle, 1, true, LocalDateTime.of(2026, 3, 11, 0, 0));

        assertThat(demanda()).containsExactly(new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 10), 3));
    }

    @Test
    void ventaConvertidaDeReserva_cuentaComoDemandaEIngreso() {
        // dado: una venta que proviene de una reserva CONVERTIDA
        Long v1 = venta(LocalDateTime.of(2026, 3, 6, 11, 0), false);
        linea(v1, variante1, 2, "90.00");
        jdbc.update("INSERT INTO reserva (variante_id, cantidad, cliente_nombre, cliente_contacto, creada_en, vence_en,"
                + " estado, venta_id, usuario_id, cerrada_en, cerrada_por) VALUES (?, 2, 'Ana', '300',"
                + " '2026-03-05 10:00:00', '2026-03-07 10:00:00', 'CONVERTIDA', ?, ?, '2026-03-06 11:00:00', ?)",
                variante1, v1, usuarioId, usuarioId);

        assertThat(ingreso()).containsExactly(new VentaNetaVariante(variante1, 2, new BigDecimal("180.00")));
        assertThat(demanda()).containsExactly(new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 6), 2));
    }

    @Test
    void ingresoNeto_lineaConDosDevolucionesParciales_noDuplicaElIngresoPorElJoin() {
        // dado: una línea de 10 x 50 con dos devoluciones parciales (2 y 3) y otra línea sin devoluciones
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        Long detalle = linea(v1, variante1, 10, "50.00");
        linea(v1, variante2, 4, "25.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 3, 9, 0));
        devolucion(detalle, 3, true, LocalDateTime.of(2026, 3, 4, 9, 0));

        // entonces: 10 - 5 = 5 unidades (250.00), una sola vez; la otra variante intacta
        assertThat(ingreso()).containsExactly(
                new VentaNetaVariante(variante1, 5, new BigDecimal("250.00")),
                new VentaNetaVariante(variante2, 4, new BigDecimal("100.00")));
    }

    @Test
    void ventaAnuladaConDevolucion_seExcluyeDeIngresoYDeDemanda() {
        // dado: venta ANULADA con una devolución apta dentro del rango
        Long anulada = venta(LocalDateTime.of(2026, 3, 2, 10, 0), true);
        Long detalle = linea(anulada, variante1, 5, "100.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 4, 9, 0));

        // entonces: ni la venta ni la devolución cuentan
        assertThat(ingreso()).isEmpty();
        assertThat(demanda()).isEmpty();
    }

    @Test
    void demandaDiaria_devolucionDeVentaAnuladaConVentaRegistradaDelMismoDia_noRestaDeLaOtra() {
        // dado: venta registrada de 3 el día 2 y una venta anulada con devolución apta el mismo día 4
        Long ok = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        linea(ok, variante1, 3, "100.00");
        Long anulada = venta(LocalDateTime.of(2026, 3, 2, 12, 0), true);
        Long detalle = linea(anulada, variante1, 5, "100.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 4, 9, 0));

        // entonces: solo la venta registrada
        assertThat(demanda()).containsExactly(new DemandaDiaVariante(variante1, LocalDate.of(2026, 3, 2), 3));
    }

    @Test
    void demandaDiaria_ventaYDevolucionAptaEnElMismoDiaQueSeCancelan_noGeneraFila() {
        // dado: vende 2 y devuelve 2 aptas el mismo día (neto 0)
        Long v1 = venta(LocalDateTime.of(2026, 3, 2, 10, 0), false);
        Long detalle = linea(v1, variante1, 2, "100.00");
        devolucion(detalle, 2, true, LocalDateTime.of(2026, 3, 2, 18, 0));

        assertThat(demanda()).isEmpty();
    }

    @Test
    void rangoInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> consultas.demandaDiariaNeta(HASTA, DESDE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consultas.ingresoNetoPorVariante(DESDE, DESDE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
