package co.livorno.controlstock.analitica.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B3, fase 5: {@link CalculoAnaliticoService} contra MySQL real. El corte es una fecha lejana (2020-06-01) para que
 * ningún dato de otras pruebas caiga en las ventanas: ABC [2019-06-01, 2020-06-01), ROP [2020-03-03, 2020-06-01).
 *
 * <p>Catálogo (producto con lead time 10, creado 2020-01-01 salvo V3):
 * <ul>
 *   <li>V1: 18 ventas de 5 u a $60 cada 5 días desde el 3 de marzo → 90 u, $5.400. NORMAL.</li>
 *   <li>V2: 3 ventas de 3 u a $100 (9 u, $900) + una venta ANULADA de 50 u. POISSON.</li>
 *   <li>V3: creada el 22 de mayo (10 días), 10 u a $100 ($1.000). SIN_HISTORIA.</li>
 *   <li>V4: sin ventas. SIN_DEMANDA.</li>
 *   <li>V5: inactiva con muchas ventas y valores previos; V6: de un producto inactivo.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class CalculoAnaliticoServiceIntegracionTest {

    static final LocalDate CORTE = LocalDate.of(2020, 6, 1);
    static final LocalDateTime CREADA = LocalDateTime.of(2020, 1, 1, 8, 0);

    @Autowired CalculoAnaliticoService servicio;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired VarianteRepository variantes;
    @Autowired MovimientoService movimientos;
    @Autowired TransactionTemplate transaccion;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AnaliticaVarianteRepository repositorioAnalitica;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> variantesCreadas = new ArrayList<>();
    private final List<Long> ejecucionesCreadas = new ArrayList<>();
    private Long usuarioId;
    private String sufijo;

    @BeforeEach
    void preparar() {
        sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        String email = "cal" + sufijo + "@livorno.test";
        jdbc.update("INSERT INTO usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,"
                + " actualizado_en) VALUES ('Calculo', ?, 'hash', 'ADMIN', TRUE, FALSE, NOW(6), NOW(6))", email);
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM devolucion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM detalle_venta WHERE venta_id IN (SELECT id FROM venta WHERE usuario_id = ?)",
                usuarioId);
        jdbc.update("DELETE FROM venta WHERE usuario_id = ?", usuarioId);
        for (Long v : variantesCreadas) {
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM reserva WHERE variante_id = ?", v);
        }
        for (Long e : ejecucionesCreadas) {
            jdbc.update("DELETE FROM analitica_variante WHERE ejecucion_id = ?", e);
        }
        for (Long v : variantesCreadas) {
            jdbc.update("DELETE FROM analitica_variante WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        for (Long e : ejecucionesCreadas) {
            jdbc.update("DELETE FROM ejecucion_analitica WHERE id = ?", e);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    // ---------- helpers de datos ----------

    private Long producto(String codigo, boolean activo) {
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        String cod = codigo + sufijo;
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, lead_time_dias,"
                + " activo, creado_en, actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 100.00, 10, ?, NOW(6), NOW(6))",
                cod, categoria, activo);
        Long id = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, cod);
        productos.add(id);
        return id;
    }

    private Long variante(Long productoId, String talla, LocalDateTime creada, boolean activa, int fisico) {
        String sku = "K" + sufijo + "-" + talla + "-" + productoId;
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, activo,"
                + " creado_en, actualizado_en) VALUES (?, ?, ?, 'NEGRO', ?, 0, ?, ?, NOW(6))", productoId, sku, talla,
                fisico, activa, creada);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, sku);
        variantesCreadas.add(id);
        return id;
    }

    private void venta(Long varianteId, LocalDateTime fecha, int cantidad, String precio, boolean anulada) {
        jdbc.update("INSERT INTO venta (fecha, usuario_id, total, estado, anulada_en, anulada_por, motivo_anulacion)"
                + " VALUES (?, ?, 0, ?, ?, ?, ?)", fecha, usuarioId, anulada ? "ANULADA" : "REGISTRADA",
                anulada ? fecha.plusHours(1) : null, anulada ? usuarioId : null, anulada ? "prueba" : null);
        Long ventaId = jdbc.queryForObject("SELECT MAX(id) FROM venta WHERE usuario_id = ?", Long.class, usuarioId);
        jdbc.update("INSERT INTO detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,"
                + " costo_unitario) VALUES (?, ?, ?, 100.00, ?, 10.00)", ventaId, varianteId, cantidad,
                new BigDecimal(precio));
    }

    private Long nuevaEjecucion() {
        EjecucionAnalitica e = ejecuciones.save(EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null,
                LocalDateTime.now(), CORTE));
        ejecucionesCreadas.add(e.getId());
        return e.getId();
    }

    private Map<String, Object> fila(Long varianteId) {
        return jdbc.queryForMap("SELECT * FROM analitica_variante WHERE variante_id = ?", varianteId);
    }

    private Map<String, Object> variante(Long id) {
        return jdbc.queryForMap("SELECT punto_reorden, clase_abc, version, stock_fisico, actualizado_en FROM variante"
                + " WHERE id = ?", id);
    }

    private int filasAnalitica(Long... ids) {
        int total = 0;
        for (Long id : ids) {
            total += jdbc.queryForObject("SELECT COUNT(*) FROM analitica_variante WHERE variante_id = ?",
                    Integer.class, id);
        }
        return total;
    }

    private void ventasV1(Long v1) {
        for (int k = 0; k < 18; k++) {
            venta(v1, LocalDate.of(2020, 3, 3).plusDays(5L * k).atTime(11, 0), 5, "60.00", false);
        }
    }

    /** Catálogo completo; devuelve [V1..V6]. */
    private Long[] catalogo() {
        Long p = producto("P", true);
        Long v1 = variante(p, "S", CREADA, true, 0);
        Long v2 = variante(p, "M", CREADA, true, 0);
        Long v3 = variante(p, "L", LocalDateTime.of(2020, 5, 22, 8, 0), true, 0);
        Long v4 = variante(p, "XL", CREADA, true, 0);
        Long v5 = variante(p, "XS", CREADA, false, 0);
        Long pInactivo = producto("Q", false);
        Long v6 = variante(pInactivo, "M", CREADA, true, 0);

        ventasV1(v1);
        venta(v2, LocalDateTime.of(2020, 4, 1, 10, 0), 3, "100.00", false);
        venta(v2, LocalDateTime.of(2020, 5, 1, 10, 0), 3, "100.00", false);
        venta(v2, LocalDateTime.of(2020, 5, 20, 10, 0), 3, "100.00", false);
        venta(v2, LocalDateTime.of(2020, 4, 10, 10, 0), 50, "100.00", true);
        venta(v3, LocalDateTime.of(2020, 5, 25, 10, 0), 10, "100.00", false);
        venta(v5, LocalDateTime.of(2020, 4, 15, 10, 0), 1000, "100.00", false);
        venta(v6, LocalDateTime.of(2020, 4, 16, 10, 0), 1000, "100.00", false);
        // valores previos en las inactivas: deben quedar en null
        jdbc.update("UPDATE variante SET punto_reorden = 9, clase_abc = 'A' WHERE id IN (?, ?)", v5, v6);
        return new Long[] {v1, v2, v3, v4, v5, v6};
    }

    // ---------- pruebas ----------

    @Test
    void calcularYAplicar_catalogoPequeno_clasesYRopEsperadosCalculadosAMano() {
        // dado: el catálogo descrito en la clase
        Long[] v = catalogo();
        Long ejecucion = nuevaEjecucion();
        Map<String, Object> antes = variante(v[0]);

        // cuando
        ResultadoCalculo r = servicio.calcularYAplicar(CORTE, ejecucion);

        // entonces: ABC por ingreso neto sobre el universo activo (total 5400 + 1000 + 900 = 7300; V5/V6 y la
        // venta anulada no cuentan): V1 0,739726 → A ; V3 acum 0,876712 → B ; V2 acum 1,000000 → C ; V4 sin ingreso → C
        assertThat(variante(v[0]).get("clase_abc")).isEqualTo("A");
        assertThat(variante(v[2]).get("clase_abc")).isEqualTo("B");
        assertThat(variante(v[1]).get("clase_abc")).isEqualTo("C");
        assertThat(variante(v[3]).get("clase_abc")).isEqualTo("C");

        // V1 NORMAL: d̄ = 90/90 = 1, s = sqrt(360/89) = 2,0112, L = 10 → μL = 10 ; SS = 1,65 × 2,0112 × √10 = 10,49 ;
        // ROP = ceil(20,49) = 21
        Map<String, Object> f1 = fila(v[0]);
        assertThat(variante(v[0]).get("punto_reorden")).isEqualTo(21);
        assertThat(f1.get("metodo_reorden")).isEqualTo("NORMAL");
        assertThat(f1.get("punto_reorden")).isEqualTo(21);
        assertThat((BigDecimal) f1.get("ingreso_neto")).isEqualByComparingTo("5400.00");
        assertThat(f1.get("unidades_netas")).isEqualTo(90);
        assertThat(f1.get("posicion")).isEqualTo(1);
        assertThat((BigDecimal) f1.get("participacion")).isEqualByComparingTo("0.739726");
        assertThat((BigDecimal) f1.get("acumulado")).isEqualByComparingTo("0.739726");
        assertThat(f1.get("dias_historia")).isEqualTo(90);
        assertThat((BigDecimal) f1.get("demanda_media")).isEqualByComparingTo("1.0000");
        assertThat((BigDecimal) f1.get("desviacion")).isEqualByComparingTo("2.0112");
        assertThat((BigDecimal) f1.get("stock_seguridad")).isEqualByComparingTo("10.49");
        assertThat((BigDecimal) f1.get("nivel_servicio")).isEqualByComparingTo("0.950");
        assertThat((BigDecimal) f1.get("z")).isEqualByComparingTo("1.65");
        assertThat(f1.get("lead_time_dias")).isEqualTo(10);
        assertThat(f1.get("ejecucion_id")).isEqualTo(ejecucion);

        // V2 POISSON, clase C (p = 0,80): μL = 9 × 10 / 90 = 1 ; P(≤1) = 0,736 < 0,8 ≤ P(≤2) = 0,920 → ROP 2
        Map<String, Object> f2 = fila(v[1]);
        assertThat(f2.get("metodo_reorden")).isEqualTo("POISSON");
        assertThat(f2.get("punto_reorden")).isEqualTo(2);
        assertThat(variante(v[1]).get("punto_reorden")).isEqualTo(2);
        assertThat((BigDecimal) f2.get("ingreso_neto")).isEqualByComparingTo("900.00");
        assertThat(f2.get("unidades_netas")).isEqualTo(9);
        assertThat((BigDecimal) f2.get("acumulado")).isEqualByComparingTo("1.000000");
        assertThat((BigDecimal) f2.get("demanda_media")).isEqualByComparingTo("0.1000");

        // V3 SIN_HISTORIA (10 días < 30): ROP null, el umbral sigue siendo stockMinimo
        Map<String, Object> f3 = fila(v[2]);
        assertThat(f3.get("metodo_reorden")).isEqualTo("SIN_HISTORIA");
        assertThat(f3.get("punto_reorden")).isNull();
        assertThat(f3.get("dias_historia")).isEqualTo(10);
        assertThat(variante(v[2]).get("punto_reorden")).isNull();
        assertThat((BigDecimal) f3.get("acumulado")).isEqualByComparingTo("0.876712");

        // V4 SIN_DEMANDA: ROP 0 y clase C
        Map<String, Object> f4 = fila(v[3]);
        assertThat(f4.get("metodo_reorden")).isEqualTo("SIN_DEMANDA");
        assertThat(f4.get("punto_reorden")).isEqualTo(0);
        assertThat(variante(v[3]).get("punto_reorden")).isEqualTo(0);
        assertThat((BigDecimal) f4.get("ingreso_neto")).isEqualByComparingTo("0.00");

        // inactivas (variante inactiva y producto inactivo): valores previos limpiados y sin fila en la instantánea
        assertThat(variante(v[4]).get("punto_reorden")).isNull();
        assertThat(variante(v[4]).get("clase_abc")).isNull();
        assertThat(variante(v[5]).get("punto_reorden")).isNull();
        assertThat(variante(v[5]).get("clase_abc")).isNull();
        assertThat(filasAnalitica(v[4], v[5])).isZero();

        // conteos: coherentes con todo el universo activo de la base; mis variantes aportan 1 A, 1 B y 2 C con 3 ROP
        Integer activas = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1", Integer.class);
        assertThat(r.variantesAnalizadas()).isEqualTo(activas);
        assertThat(r.claseA() + r.claseB() + r.claseC()).isEqualTo(activas);
        assertThat(r.claseA()).isGreaterThanOrEqualTo(1);
        assertThat(r.claseB()).isGreaterThanOrEqualTo(1);
        assertThat(r.claseC()).isGreaterThanOrEqualTo(2);
        Integer conRop = jdbc.queryForObject("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id"
                + " WHERE v.activo = 1 AND p.activo = 1 AND v.punto_reorden IS NOT NULL", Integer.class);
        assertThat(r.conPuntoReorden()).isEqualTo(conRop);

        // el cálculo no toca versión, stock ni actualizado_en
        Map<String, Object> despues = variante(v[0]);
        assertThat(despues.get("version")).isEqualTo(antes.get("version"));
        assertThat(despues.get("stock_fisico")).isEqualTo(antes.get("stock_fisico"));
        assertThat(despues.get("actualizado_en")).isEqualTo(antes.get("actualizado_en"));
    }

    @Test
    void calcularYAplicar_sinVentasEnLaVentana_todasClaseCYSinDemanda() {
        // dado: variantes activas con >= 30 días de historia y ninguna venta en la ventana (total de ingreso 0)
        Long p = producto("Z", true);
        Long a = variante(p, "S", CREADA, true, 0);
        Long b = variante(p, "M", CREADA, true, 0);
        Long nueva = variante(p, "L", LocalDateTime.of(2020, 5, 22, 8, 0), true, 0);
        Long ejecucion = nuevaEjecucion();
        assertThat(filasAnalitica(a, b, nueva)).isZero(); // primera ejecución: sin filas previas

        // cuando
        ResultadoCalculo r = servicio.calcularYAplicar(CORTE, ejecucion);

        // entonces: todas C, ROP 0 si tienen historia suficiente y null si son nuevas; ninguna A ni B en el universo
        assertThat(variante(a).get("clase_abc")).isEqualTo("C");
        assertThat(variante(b).get("clase_abc")).isEqualTo("C");
        assertThat(variante(nueva).get("clase_abc")).isEqualTo("C");
        assertThat(fila(a).get("metodo_reorden")).isEqualTo("SIN_DEMANDA");
        assertThat(variante(a).get("punto_reorden")).isEqualTo(0);
        assertThat(fila(nueva).get("metodo_reorden")).isEqualTo("SIN_HISTORIA");
        assertThat(variante(nueva).get("punto_reorden")).isNull();
        assertThat((BigDecimal) fila(a).get("ingreso_neto")).isEqualByComparingTo("0.00");
        assertThat(r.claseA()).isZero();
        assertThat(r.claseB()).isZero();
        assertThat(r.claseC()).isEqualTo(r.variantesAnalizadas());
    }

    @Test
    void calcularYAplicar_segundaEjecucion_reemplazaLaInstantaneaYRecalcula() {
        // dado: una primera ejecución y luego una venta nueva de V4 (3 u a $100 el 30 de mayo)
        Long[] v = catalogo();
        Long primera = nuevaEjecucion();
        servicio.calcularYAplicar(CORTE, primera);
        assertThat(fila(v[3]).get("metodo_reorden")).isEqualTo("SIN_DEMANDA");
        venta(v[3], LocalDateTime.of(2020, 5, 30, 10, 0), 3, "100.00", false);
        Long segunda = nuevaEjecucion();

        // cuando
        servicio.calcularYAplicar(CORTE, segunda);

        // entonces: una sola fila por variante, enlazada a la segunda ejecución, ninguna a la primera
        assertThat(filasAnalitica(v[0], v[1], v[2], v[3])).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_variante WHERE ejecucion_id = ?",
                Integer.class, primera)).isZero();
        assertThat(fila(v[3]).get("ejecucion_id")).isEqualTo(segunda);
        // V4: μL = 3 × 10 / 90 = 0,333 ; clase C (p 0,8): P(0) = 0,717 < 0,8 ≤ P(≤1) = 0,955 → ROP 1
        assertThat(fila(v[3]).get("metodo_reorden")).isEqualTo("POISSON");
        assertThat(variante(v[3]).get("punto_reorden")).isEqualTo(1);
        // total 7600: V1 0,710526 A ; V3 acum 0,842105 B ; V2 acum 0,960526 C ; V4 C
        assertThat(variante(v[0]).get("clase_abc")).isEqualTo("A");
        assertThat(variante(v[2]).get("clase_abc")).isEqualTo("B");
        assertThat((BigDecimal) fila(v[1]).get("acumulado")).isEqualByComparingTo("0.960526");
        assertThat(variante(v[1]).get("clase_abc")).isEqualTo("C");
    }

    @Test
    void calcularYAplicar_fallaTrasLosUpdate_revierteTodoYConservaLosValoresAnteriores() {
        // dado: una primera ejecución exitosa y una venta nueva que cambiaría V4
        Long[] v = catalogo();
        Long primera = nuevaEjecucion();
        servicio.calcularYAplicar(CORTE, primera);
        venta(v[3], LocalDateTime.of(2020, 5, 30, 10, 0), 3, "100.00", false);
        Long segunda = nuevaEjecucion();
        doThrow(new IllegalStateException("fallo forzado")).when(repositorioAnalitica).saveAll(org.mockito.ArgumentMatchers.anyList());

        // cuando: la segunda ejecución falla en el último paso, después de la instantánea y de los UPDATE
        assertThatThrownBy(() -> servicio.calcularYAplicar(CORTE, segunda))
                .isInstanceOf(IllegalStateException.class).hasMessage("fallo forzado");

        // entonces: todo revertido; valores anteriores de la variante y de la instantánea intactos
        assertThat(variante(v[0]).get("punto_reorden")).isEqualTo(21);
        assertThat(variante(v[1]).get("punto_reorden")).isEqualTo(2);
        assertThat(variante(v[2]).get("punto_reorden")).isNull();
        assertThat(variante(v[3]).get("punto_reorden")).isEqualTo(0);
        assertThat(variante(v[0]).get("clase_abc")).isEqualTo("A");
        assertThat(variante(v[2]).get("clase_abc")).isEqualTo("B");
        assertThat(variante(v[4]).get("punto_reorden")).isNull();
        assertThat(fila(v[3]).get("ejecucion_id")).isEqualTo(primera);
        assertThat(fila(v[3]).get("metodo_reorden")).isEqualTo("SIN_DEMANDA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analitica_variante WHERE ejecucion_id = ?",
                Integer.class, segunda)).isZero();
    }

    @Test
    void calcularYAplicar_ventaConcurrenteConCargaPrevia_ventaPasaSin409YRopIntacto() throws Exception {
        // dado: una variante con ventas (ROP 21 al calcular) y stock 10; el hilo A la carga antes del cálculo
        Long p = producto("C", true);
        Long id = variante(p, "M", CREADA, true, 10);
        ventasV1(id);
        Long ejecucion = nuevaEjecucion();
        long versionInicial = ((Number) variante(id).get("version")).longValue();
        CountDownLatch cargada = new CountDownLatch(1);
        CountDownLatch calculado = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> hiloA = pool.submit(() -> {
                try {
                    transaccion.executeWithoutResult(estado -> {
                        variantes.findById(id).orElseThrow(); // entidad con puntoReorden null en memoria
                        cargada.countDown();
                        try {
                            if (!calculado.await(60, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("tiempo agotado");
                            }
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        movimientos.registrar(new SolicitudMovimiento(id, TipoMovimiento.VENTA, 1, null, usuarioId,
                                null, null, null));
                    });
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            // cuando: B calcula y confirma mientras A espera, y luego A registra la venta
            assertThat(cargada.await(60, TimeUnit.SECONDS)).isTrue();
            servicio.calcularYAplicar(CORTE, ejecucion);
            assertThat(variante(id).get("punto_reorden")).isEqualTo(21);
            assertThat(((Number) variante(id).get("version")).longValue()).isEqualTo(versionInicial);
            calculado.countDown();
            Throwable error = hiloA.get(60, TimeUnit.SECONDS);

            // entonces: la venta pasó sin 409, el ROP y la clase del cálculo siguen, y la versión solo subió por la venta
            assertThat(error).isNull();
            Map<String, Object> v = variante(id);
            assertThat(v.get("punto_reorden")).isEqualTo(21);
            assertThat(v.get("clase_abc")).isEqualTo("A");
            assertThat(v.get("stock_fisico")).isEqualTo(9);
            assertThat(((Number) v.get("version")).longValue()).isEqualTo(versionInicial + 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void calcularYAplicar_ventaConMovimientoInsertadoSinConfirmar_ventaConfirmaSin409YCalculoNoSeBloquea()
            throws Exception {
        // dado: V1 con ventas (su ROP cambiaría a 21) y V2 sin ventas (ROP pasaría a 0), ambas con stock
        Long p = producto("D", true);
        Long v1 = variante(p, "S", CREADA, true, 10);
        Long v2 = variante(p, "M", CREADA, true, 10);
        ventasV1(v1);
        Long ejecucion = nuevaEjecucion();
        long versionInicial = ((Number) variante(v1).get("version")).longValue();
        CountDownLatch insertado = new CountDownLatch(1);
        CountDownLatch calculado = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // el hilo A registra una venta de V1: el movimiento ya está insertado (S sobre la variante por la FK) y
            // su UPDATE de variante llegará al confirmar, después de que el cálculo termine
            Future<Throwable> hiloA = pool.submit(() -> {
                try {
                    transaccion.executeWithoutResult(estado -> {
                        movimientos.registrar(new SolicitudMovimiento(v1, TipoMovimiento.VENTA, 1, null, usuarioId,
                                null, null, null));
                        insertado.countDown();
                        try {
                            if (!calculado.await(60, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("tiempo agotado");
                            }
                        } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
            assertThat(insertado.await(60, TimeUnit.SECONDS)).isTrue();

            // cuando: el cálculo corre con la venta en curso (no debe esperarla ni interbloquearse)
            Future<ResultadoCalculo> hiloB = pool.submit(() -> servicio.calcularYAplicar(CORTE, ejecucion));
            ResultadoCalculo r = hiloB.get(30, TimeUnit.SECONDS);
            calculado.countDown();
            Throwable error = hiloA.get(60, TimeUnit.SECONDS);

            // entonces: la venta confirma sin 409; V2 (libre) se actualizó; V1, ocupada todo el cálculo, conserva su
            // valor anterior hasta el siguiente cálculo y la instantánea sí quedó completa
            assertThat(error).isNull();
            assertThat(r.variantesAnalizadas()).isGreaterThanOrEqualTo(2);
            assertThat(variante(v2).get("punto_reorden")).isEqualTo(0);
            assertThat(variante(v1).get("punto_reorden")).isNull();
            assertThat(((Number) variante(v1).get("version")).longValue()).isEqualTo(versionInicial + 1);
            assertThat(variante(v1).get("stock_fisico")).isEqualTo(9);
            assertThat(fila(v1).get("punto_reorden")).isEqualTo(21);

            // y el siguiente cálculo, ya sin ventas en curso, lo aplica
            Long siguiente = nuevaEjecucion();
            servicio.calcularYAplicar(CORTE, siguiente);
            assertThat(variante(v1).get("punto_reorden")).isEqualTo(21);
            assertThat(variante(v1).get("clase_abc")).isEqualTo("A");
        } finally {
            pool.shutdownNow();
        }
    }
}
