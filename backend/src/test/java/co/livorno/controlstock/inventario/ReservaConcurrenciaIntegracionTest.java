package co.livorno.controlstock.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.inventario.dto.CancelarReservaRequest;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.LineaReservaRequest;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.dto.SolicitudMovimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import co.livorno.controlstock.inventario.repositorio.ReservaRepository;
import co.livorno.controlstock.inventario.servicio.MovimientoService;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.dto.ConvertirReservasRequest;
import co.livorno.controlstock.venta.dto.LineaVentaRequest;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.servicio.VentaService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
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
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Concurrencia y consistencia de reservas, vencimiento y conversión contra MySQL real (T1, fase 3). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class ReservaConcurrenciaIntegracionTest {

    private static final String CONTACTO = "3001234567";

    @Autowired ReservaService reservaService;
    @Autowired VentaService ventaService;
    @Autowired MovimientoService movimientos;
    @Autowired ReservaRepository reservas;
    @Autowired UsuarioRepository usuarios;
    @Autowired TransactionTemplate transaccion;
    @Autowired JdbcTemplate jdbc;
    @Autowired MySQLContainer mysql;

    private Usuario usuario;
    private final List<Long> productos = new ArrayList<>();
    private final List<Long> variantesCreadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        usuario = usuarios.save(new Usuario("Admin Conc", "conc" + sufijo + "@livorno.test", "hash", Rol.ADMIN));
        autenticar();
    }

    /** El contexto de seguridad es por hilo: cada hilo de prueba debe autenticarse. */
    private void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), usuario.getNombre(), "ADMIN"), "n/a",
                List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        for (Long v : variantesCreadas) {
            List<Long> ventas = jdbc.queryForList("SELECT venta_id FROM detalle_venta WHERE variante_id = ?",
                    Long.class, v);
            jdbc.update("DELETE FROM movimiento WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM alerta WHERE variante_id = ?", v);
            jdbc.update("DELETE FROM reserva WHERE variante_id = ?", v);
            for (Long ventaId : ventas) {
                jdbc.update("DELETE FROM detalle_venta WHERE venta_id = ?", ventaId);
                jdbc.update("DELETE FROM venta WHERE id = ?", ventaId);
            }
            jdbc.update("DELETE FROM variante WHERE id = ?", v);
        }
        for (Long p : productos) {
            jdbc.update("DELETE FROM producto WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuario.getId());
    }

    private Long crearVariante(int fisico, int reservado) {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        Long categoria = jdbc.queryForObject("SELECT id FROM categoria ORDER BY id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en,"
                + " actualizado_en) VALUES (?, 'Prueba', ?, 10.00, 20.00, NOW(6), NOW(6))", "R" + sufijo, categoria);
        Long producto = jdbc.queryForObject("SELECT id FROM producto WHERE codigo = ?", Long.class, "R" + sufijo);
        productos.add(producto);
        jdbc.update("INSERT INTO variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, creado_en,"
                + " actualizado_en) VALUES (?, ?, 'M', 'NEGRO', ?, ?, NOW(6), NOW(6))",
                producto, "R" + sufijo + "-M-NEG", fisico, reservado);
        Long id = jdbc.queryForObject("SELECT id FROM variante WHERE sku = ?", Long.class, "R" + sufijo + "-M-NEG");
        variantesCreadas.add(id);
        return id;
    }

    private ReservaResponse reservar(Long varianteId, int cantidad) {
        return reservaService.crear(new CrearReservaRequest("Ana Pérez", CONTACTO, 1,
                List.of(new LineaReservaRequest(varianteId, cantidad)))).get(0);
    }

    private ConvertirReservasRequest convertir(Long... ids) {
        return new ConvertirReservasRequest(List.of(ids), null);
    }

    private int[] stock(Long id) {
        Map<String, Object> f = jdbc.queryForMap("SELECT stock_fisico, stock_reservado FROM variante WHERE id = ?", id);
        return new int[] {((Number) f.get("stock_fisico")).intValue(), ((Number) f.get("stock_reservado")).intValue()};
    }

    private int contarMovimientos(Long varianteId, String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE variante_id = ? AND tipo = ?",
                Integer.class, varianteId, tipo);
    }

    private String estado(Long reservaId) {
        return jdbc.queryForObject("SELECT estado FROM reserva WHERE id = ?", String.class, reservaId);
    }

    /** Espera a que alguna transacción esté bloqueada esperando un candado de fila (se consulta como root). */
    private void esperarTransaccionBloqueada() throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try (Connection c = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword())) {
            while (System.nanoTime() < limite) {
                try (Statement st = c.createStatement();
                        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Ninguna transacción quedó esperando el candado de la reserva");
    }

    /** Ejecuta la tarea autenticada y devuelve null si triunfa o la excepción lanzada. */
    private Callable<Throwable> intento(Runnable tarea) {
        return () -> {
            autenticar();
            try {
                tarea.run();
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private static Throwable esperar(Future<Throwable> f) throws Exception {
        return f.get(60, TimeUnit.SECONDS);
    }

    private static void espera(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 1 y 2: vencimiento contra conversión ----------

    @Test
    void vencer_mientrasUnaConversionTieneBloqueadaLaReserva_esperaYLaReservaQuedaConvertidaSinLiberacion()
            throws Exception {
        // dado: reserva de 2; A la bloquea y convierte, B intenta vencerla y queda esperando
        Long v = crearVariante(5, 0);
        ReservaResponse r = reservar(v, 2);
        LocalDateTime despues = r.venceEn().plusMinutes(5);
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> a = pool.submit(intento(() -> transaccion.executeWithoutResult(estado -> {
                reservas.bloquear(r.id()).orElseThrow();
                bloqueada.countDown();
                espera(continuar);
                ventaService.registrarDesdeReservas(convertir(r.id()));
            })));
            espera(bloqueada);
            boolean[] vencio = {true};
            Future<Throwable> b = pool.submit(intento(() -> vencio[0] = reservaService.vencer(r.id(), despues)));
            esperarTransaccionBloqueada();

            // cuando: A convierte y confirma
            continuar.countDown();
            Throwable errorA = esperar(a);
            Throwable errorB = esperar(b);

            // entonces: gana la conversión; B se omite sin error y no libera nada
            assertThat(errorA).isNull();
            assertThat(errorB).isNull();
            assertThat(vencio[0]).isFalse();
            assertThat(estado(r.id())).isEqualTo("CONVERTIDA");
            assertThat(contarMovimientos(v, "VENTA_RESERVA")).isEqualTo(1);
            assertThat(contarMovimientos(v, "LIBERACION")).isZero();
            assertThat(stock(v)).containsExactly(3, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void registrarDesdeReservas_mientrasElVencimientoTieneBloqueadaLaReserva_recibeConflictoYNoVende()
            throws Exception {
        // dado: A vence la reserva y mantiene su transacción abierta; B intenta convertirla y queda esperando
        Long v = crearVariante(5, 0);
        ReservaResponse r = reservar(v, 2);
        LocalDateTime despues = r.venceEn().plusMinutes(5);
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch continuar = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> a = pool.submit(intento(() -> transaccion.executeWithoutResult(estado -> {
                assertThat(reservaService.vencer(r.id(), despues)).isTrue();
                bloqueada.countDown();
                espera(continuar);
            })));
            espera(bloqueada);
            Future<Throwable> b = pool.submit(intento(() -> ventaService.registrarDesdeReservas(convertir(r.id()))));
            esperarTransaccionBloqueada();

            // cuando: el vencimiento confirma
            continuar.countDown();
            Throwable errorA = esperar(a);
            Throwable errorB = esperar(b);

            // entonces: la conversión recibe 409, queda VENCIDA, una LIBERACION y ninguna venta
            assertThat(errorA).isNull();
            assertThat(errorB).isInstanceOf(ConflictoException.class);
            assertThat(estado(r.id())).isEqualTo("VENCIDA");
            assertThat(contarMovimientos(v, "LIBERACION")).isEqualTo(1);
            assertThat(contarMovimientos(v, "VENTA_RESERVA")).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM detalle_venta WHERE variante_id = ?",
                    Integer.class, v)).isZero();
            assertThat(stock(v)).containsExactly(5, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- 3: cancelar contra convertir ----------

    @Test
    void cancelarYConvertir_simultaneasSobreLaMismaReserva_exactamenteUnaGanaEnCadaRonda() throws Exception {
        Long v = crearVariante(50, 0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int ronda = 0; ronda < 8; ronda++) {
                // dado: una reserva nueva y dos hilos que arrancan a la vez
                ReservaResponse r = reservar(v, 1);
                CountDownLatch salida = new CountDownLatch(1);
                Future<Throwable> cancelar = pool.submit(intento(() -> {
                    espera(salida);
                    reservaService.cancelar(r.id(), new CancelarReservaRequest("Cliente desistió"));
                }));
                Future<Throwable> convertir = pool.submit(intento(() -> {
                    espera(salida);
                    ventaService.registrarDesdeReservas(convertir(r.id()));
                }));

                // cuando
                salida.countDown();
                Throwable errorCancelar = esperar(cancelar);
                Throwable errorConvertir = esperar(convertir);

                // entonces: uno triunfa y el otro recibe 409; el kardex solo registra al ganador
                String estadoFinal = estado(r.id());
                if (errorCancelar == null) {
                    assertThat(errorConvertir).isInstanceOf(ConflictoException.class);
                    assertThat(estadoFinal).isEqualTo("CANCELADA");
                } else {
                    assertThat(errorCancelar).isInstanceOf(ConflictoException.class);
                    assertThat(errorConvertir).isNull();
                    assertThat(estadoFinal).isEqualTo("CONVERTIDA");
                }
                assertThat(stock(v)[1]).isZero();
                assertThat(contarMovimientos(v, "VENTA_RESERVA") + contarMovimientos(v, "LIBERACION"))
                        .isEqualTo(ronda + 1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- 4: última unidad ----------

    @Test
    void crear_variasReservasSobreLaUltimaUnidad_exactamenteUnaTriunfaYElRestoFalla() throws Exception {
        // dado: disponible 1 y 6 hilos que reservan 1 a la vez
        Long v = crearVariante(1, 0);
        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                futuros.add(pool.submit(intento(() -> {
                    espera(salida);
                    reservaService.crear(new CrearReservaRequest("Cliente", CONTACTO, 1,
                            List.of(new LineaReservaRequest(v, 1))));
                })));
            }

            // cuando
            salida.countDown();
            int exitos = contarExitos(futuros);

            // entonces: una sola reserva ACTIVA, reservado 1, disponible 0 y nada huérfano
            assertThat(exitos).isEqualTo(1);
            assertThat(stock(v)).containsExactly(1, 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE variante_id = ?", Integer.class, v))
                    .isEqualTo(1);
            assertThat(contarMovimientos(v, "RESERVA")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- 5: reserva contra venta directa ----------

    @Test
    void reservarYVenderDirecto_alMismoTiempoSobreLaMismaVariante_nuncaDejaDisponibleNegativo() throws Exception {
        // dado: físico 4; 4 hilos reservan 2 y 4 venden 2 (solo caben 2 operaciones)
        Long v = crearVariante(4, 0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Throwable>> reservasF = new ArrayList<>();
            List<Future<Throwable>> ventasF = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                reservasF.add(pool.submit(intento(() -> {
                    espera(salida);
                    reservaService.crear(new CrearReservaRequest("Cliente", CONTACTO, 1,
                            List.of(new LineaReservaRequest(v, 2))));
                })));
                ventasF.add(pool.submit(intento(() -> {
                    espera(salida);
                    ventaService.registrar(new RegistrarVentaRequest(List.of(new LineaVentaRequest(v, 2, null))));
                })));
            }

            // cuando
            salida.countDown();
            int reservasOk = contarExitos(reservasF);
            int ventasOk = contarExitos(ventasF);

            // entonces: invariantes de stock intactos
            int[] s = stock(v);
            assertThat(reservasOk + ventasOk).isBetween(1, 2);
            assertThat(s[0]).isEqualTo(4 - 2 * ventasOk);
            assertThat(s[1]).isEqualTo(2 * reservasOk);
            assertThat(s[0] - s[1]).isGreaterThanOrEqualTo(0);
            assertThat(sumaReservasActivas(v)).isEqualTo(s[1]);
            verificarKardex(v, 4, 0);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Cuenta éxitos; todo fallo debe ser 422 (stock) o conflicto de concurrencia (409). */
    private int contarExitos(List<Future<Throwable>> futuros) throws Exception {
        int exitos = 0;
        for (Future<Throwable> f : futuros) {
            Throwable t = esperar(f);
            if (t == null) {
                exitos++;
            } else {
                assertThat(t).isInstanceOfAny(ExcepcionNegocio.class, ConcurrencyFailureException.class);
            }
        }
        return exitos;
    }

    // ---------- 6: consistencia tras operaciones aleatorias ----------

    @Test
    void operacionesAleatorias_reservarCancelarVencerConvertirVenderEntrar_reservadoIgualASumaDeActivasYStockIgualAlKardex() {
        // dado: 2 variantes con stock inicial
        Random azar = new Random(20261002L);
        Long[] ids = {crearVariante(20, 0), crearVariante(12, 0)};
        int[] inicialFisico = {20, 12};
        LocalDateTime lejano = LocalDateTime.now().plusDays(30);

        // cuando: 150 operaciones aleatorias (las que exceden el disponible se rechazan con 422)
        for (int paso = 0; paso < 150; paso++) {
            final int i = azar.nextInt(ids.length);
            final int q = 1 + azar.nextInt(3);
            List<Long> activas = jdbc.queryForList("SELECT id FROM reserva WHERE estado = 'ACTIVA' AND variante_id"
                    + " IN (?, ?) ORDER BY id", Long.class, ids[0], ids[1]);
            final int disponible = stock(ids[i])[0] - stock(ids[i])[1];
            switch (azar.nextInt(6)) {
                case 0 -> {
                    if (q <= disponible) {
                        reservar(ids[i], q);
                    } else {
                        assertThatThrownBy(() -> reservar(ids[i], disponible + 1)).isInstanceOf(ExcepcionNegocio.class);
                    }
                }
                case 1 -> {
                    if (!activas.isEmpty()) {
                        reservaService.cancelar(activas.get(azar.nextInt(activas.size())),
                                new CancelarReservaRequest("Cliente desistió"));
                    }
                }
                case 2 -> {
                    if (!activas.isEmpty()) {
                        assertThat(reservaService.vencer(activas.get(azar.nextInt(activas.size())), lejano)).isTrue();
                    }
                }
                case 3 -> {
                    if (!activas.isEmpty()) {
                        int cuantas = Math.min(activas.size(), 1 + azar.nextInt(2));
                        List<Long> elegidas = new ArrayList<>(activas);
                        Collections.shuffle(elegidas, azar);
                        ventaService.registrarDesdeReservas(
                                new ConvertirReservasRequest(elegidas.subList(0, cuantas), null));
                    }
                }
                case 4 -> {
                    if (q <= disponible) {
                        ventaService.registrar(new RegistrarVentaRequest(
                                List.of(new LineaVentaRequest(ids[i], q, null))));
                    }
                }
                default -> movimientos.registrar(new SolicitudMovimiento(ids[i], TipoMovimiento.ENTRADA, q,
                        new BigDecimal("10.00"), null, null, null, null));
            }
        }

        // entonces: reservado = suma de ACTIVAS; stock = inicial + kardex; cadena continua; 1 RESERVA por reserva
        for (int i = 0; i < ids.length; i++) {
            int[] s = stock(ids[i]);
            assertThat(s[1]).isEqualTo(sumaReservasActivas(ids[i]));
            assertThat(s[0] - s[1]).isGreaterThanOrEqualTo(0);
            verificarKardex(ids[i], inicialFisico[i], 0);
            int totalReservas = jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE variante_id = ?",
                    Integer.class, ids[i]);
            int cerradas = jdbc.queryForObject("SELECT COUNT(*) FROM reserva WHERE variante_id = ? AND estado IN"
                    + " ('CANCELADA', 'VENCIDA')", Integer.class, ids[i]);
            assertThat(contarMovimientos(ids[i], "RESERVA")).isEqualTo(totalReservas);
            assertThat(contarMovimientos(ids[i], "LIBERACION")).isEqualTo(cerradas);
        }
    }

    private int sumaReservasActivas(Long varianteId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM reserva WHERE variante_id = ? AND"
                + " estado = 'ACTIVA'", Integer.class, varianteId);
    }

    /** Stock actual = inicial + suma del kardex, y la cadena antes/después es continua. */
    private void verificarKardex(Long id, int fisicoInicial, int reservadoInicial) {
        int sumaFisico = jdbc.queryForObject("SELECT COALESCE(SUM(fisico_despues - fisico_antes), 0)"
                + " FROM movimiento WHERE variante_id = ?", Integer.class, id);
        int sumaReservado = jdbc.queryForObject("SELECT COALESCE(SUM(reservado_despues - reservado_antes), 0)"
                + " FROM movimiento WHERE variante_id = ?", Integer.class, id);
        assertThat(new int[] {fisicoInicial + sumaFisico, reservadoInicial + sumaReservado})
                .containsExactly(stock(id));
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
