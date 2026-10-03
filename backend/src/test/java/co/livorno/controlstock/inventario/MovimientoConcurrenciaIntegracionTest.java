package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Concurrencia y consistencia de {@link MovimientoService} contra MySQL real (T1, fase 2). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class MovimientoConcurrenciaIntegracionTest {

    @Autowired MovimientoService servicio;
    @Autowired VarianteRepository variantes;
    @Autowired TransactionTemplate transaccion;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> productos = new ArrayList<>();
    private final List<Long> variantesCreadas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (Long v : variantesCreadas) {
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM reserva WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
    }

    private Long crearVariante(int fisico, int reservado) {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 20.00, NOW(6), NOW(6))", "T" + sufijo, categoria);
        Long producto = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "T" + sufijo);
        productos.add(producto);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'M', 'NEGRO', ?, ?, NOW(6), NOW(6))",
                producto, "T" + sufijo + "-M-NEG", fisico, reservado);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, "T" + sufijo + "-M-NEG");
        variantesCreadas.add(id);
        return id;
    }

    private static SolicitudMovimiento venta(Long varianteId, int cantidad) {
        return new SolicitudMovimiento(varianteId, TipoMovimiento.VENTA, cantidad, null, null, null, null, null);
    }

    private int[] stock(Long id) {
        Map<String, Object> f = jdbc.queryForMap("SELECT stock_fisico, stock_reservado FROM variante WHERE id = ?", id);
        return new int[] {((Number) f.get("stock_fisico")).intValue(), ((Number) f.get("stock_reservado")).intValue()};
    }

    private int contarMovimientos(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ?", Integer.class, id);
    }

    private static final class Exito extends RuntimeException { }

    @Test
    void registrarVenta_dosTransaccionesConLaMismaLecturaConStock1_exactamenteUnaFallaPorConcurrencia()
            throws Exception {
        // dado: variante con stock 1 y dos transacciones que la cargan antes de la barrera
        Long id = crearVariante(1, 0);
        CyclicBarrier barrera = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Throwable> intento = () -> {
                try {
                    transaccion.executeWithoutResult(estado -> {
                        variantes.findById(id).orElseThrow();
                        try {
                            barrera.await(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        servicio.registrar(venta(id, 1));
                    });
                    return new Exito();
                } catch (Throwable t) {
                    return t;
                }
            };
            // cuando: ambas registran VENTA de 1
            Future<Throwable> a = pool.submit(intento);
            Future<Throwable> b = pool.submit(intento);
            List<Throwable> resultados = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));

            // entonces: una triunfa, la otra falla por concurrencia, stock 0 y un solo movimiento
            assertThat(resultados.stream().filter(r -> r instanceof Exito)).hasSize(1);
            assertThat(resultados.stream().filter(r -> r instanceof ConcurrencyFailureException)).hasSize(1);
            assertThat(stock(id)).containsExactly(0, 0);
            assertThat(contarMovimientos(id)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void registrarVenta_diezHilosSobreStock5_stockNuncaNegativoYSinErroresInesperados() throws Exception {
        // dado: stock 5 y 10 hilos que venden 1 al mismo tiempo
        Long id = crearVariante(5, 0);
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    try {
                        servicio.registrar(venta(id, 1));
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            // cuando
            salida.countDown();
            int exitos = 0;
            for (Future<Throwable> f : futuros) {
                Throwable t = f.get(60, TimeUnit.SECONDS);
                if (t == null) {
                    exitos++;
                } else {
                    // solo se admiten: stock insuficiente (422) o conflicto de concurrencia (409)
                    assertThat(t).isInstanceOfAny(ExcepcionNegocio.class, ConcurrencyFailureException.class);
                }
            }
            // entonces: stock = 5 - éxitos >= 0, un movimiento por éxito y cadena coherente
            int[] s = stock(id);
            assertThat(exitos).isBetween(1, 5);
            assertThat(s[0]).isEqualTo(5 - exitos).isGreaterThanOrEqualTo(0);
            assertThat(contarMovimientos(id)).isEqualTo(exitos);
            verificarCadena(id, 5, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void registrar_200MovimientosAleatoriosValidos_stockIgualAlEfectoAcumuladoDelKardexYCadenaContinua() {
        // dado: 3 variantes con stock inicial y un espejo del estado esperado
        Random azar = new Random(20261002L);
        Long[] ids = {crearVariante(10, 2), crearVariante(0, 0), crearVariante(30, 30)};
        int[][] espejo = {{10, 2}, {0, 0}, {30, 30}};
        int[][] inicial = {{10, 2}, {0, 0}, {30, 30}};
        TipoMovimiento[] tipos = TipoMovimiento.values();

        // cuando: 200 movimientos válidos (se descartan los que dejarían el stock inválido)
        int aplicados = 0;
        while (aplicados < 200) {
            int i = azar.nextInt(ids.length);
            TipoMovimiento tipo = tipos[azar.nextInt(tipos.length)];
            int q = 1 + azar.nextInt(5);
            int disponible = espejo[i][0] - espejo[i][1];
            boolean valido = switch (tipo) {
                case VENTA, RESERVA, AJUSTE_NEGATIVO -> q <= disponible;
                case LIBERACION, VENTA_RESERVA -> q <= espejo[i][1];
                default -> true;
            };
            if (!valido) {
                continue;
            }
            boolean entrada = tipo == TipoMovimiento.ENTRADA;
            boolean ajuste = tipo == TipoMovimiento.AJUSTE_POSITIVO || tipo == TipoMovimiento.AJUSTE_NEGATIVO;
            servicio.registrar(new SolicitudMovimiento(ids[i], tipo, q, entrada ? new BigDecimal("10.00") : null,
                    null, null, null, ajuste ? "conteo" : null));
            var efecto = tipo.efecto(q);
            espejo[i][0] += efecto.deltaFisico();
            espejo[i][1] += efecto.deltaReservado();
            aplicados++;
        }

        // entonces: la fila de la variante, el espejo y la suma del kardex coinciden; la cadena es continua
        int total = 0;
        for (int i = 0; i < ids.length; i++) {
            assertThat(stock(ids[i])).containsExactly(espejo[i][0], espejo[i][1]);
            int sumaFisico = jdbc.queryForObject("SELECT COALESCE(SUM(fisico_despues - fisico_antes), 0)"
                    + " FROM movimiento WHERE variante_id = ?", Integer.class, ids[i]);
            int sumaReservado = jdbc.queryForObject("SELECT COALESCE(SUM(reservado_despues - reservado_antes), 0)"
                    + " FROM movimiento WHERE variante_id = ?", Integer.class, ids[i]);
            assertThat(inicial[i][0] + sumaFisico).isEqualTo(espejo[i][0]);
            assertThat(inicial[i][1] + sumaReservado).isEqualTo(espejo[i][1]);
            verificarCadena(ids[i], inicial[i][0], inicial[i][1]);
            total += contarMovimientos(ids[i]);
        }
        assertThat(total).isEqualTo(200);
    }

    /** Cada "antes" debe ser el "después" del eslabón anterior (se sigue la cadena: en concurrencia el id puede no
     *  coincidir con el orden de confirmación) y el último "después" debe ser el stock actual. */
    private void verificarCadena(Long id, int fisicoInicial, int reservadoInicial) {
        List<Map<String, Object>> pendientes = new ArrayList<>(jdbc.queryForList("SELECT fisico_antes,"
                + " fisico_despues, reservado_antes, reservado_despues FROM movimiento WHERE variante_id = ?", id));
        int fisico = fisicoInicial;
        int reservado = reservadoInicial;
        while (!pendientes.isEmpty()) {
            final int f = fisico;
            final int r = reservado;
            Map<String, Object> siguiente = pendientes.stream()
                    .filter(m -> ((Number) m.get("fisico_antes")).intValue() == f
                            && ((Number) m.get("reservado_antes")).intValue() == r)
                    .findFirst().orElse(null);
            assertThat(siguiente).as("eslabón con antes = (%d, %d)", f, r).isNotNull();
            pendientes.remove(siguiente);
            fisico = ((Number) siguiente.get("fisico_despues")).intValue();
            reservado = ((Number) siguiente.get("reservado_despues")).intValue();
        }
        assertThat(new int[] {fisico, reservado}).containsExactly(stock(id));
    }
}
