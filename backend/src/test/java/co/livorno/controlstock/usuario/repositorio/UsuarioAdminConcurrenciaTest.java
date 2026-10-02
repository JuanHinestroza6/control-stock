package co.livorno.controlstock.usuario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.seguridad.UsuarioAutenticado;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.servicio.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bloqueo pesimista de {@link UsuarioRepository#bloquearAdminsActivos()}: con dos únicos ADMIN activos que se
 * degradan o desactivan mutuamente en transacciones concurrentes, solo una puede triunfar. Sin el {@code @Lock}
 * ambas leen el mismo estado (snapshot) y el sistema se queda sin administradores.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class UsuarioAdminConcurrenciaTest {

    @Autowired UsuarioService servicio;
    @Autowired UsuarioRepository usuarios;
    @Autowired TransactionTemplate transaccion;
    @Autowired JdbcTemplate jdbc;

    @Test
    void desactivarseMutuamente_losDosUnicosAdmin_unoFallaYQuedaUnoActivo() throws Exception {
        ejecutarCarrera(Accion.DESACTIVAR);
    }

    @Test
    void degradarseMutuamente_losDosUnicosAdmin_unoFallaYQuedaUnoActivo() throws Exception {
        ejecutarCarrera(Accion.DEGRADAR);
    }

    private enum Accion { DESACTIVAR, DEGRADAR }

    private void ejecutarCarrera(Accion accion) throws Exception {
        String sufijo = Long.toString(System.nanoTime(), 36);
        List<Long> adminsPrevios = jdbc.queryForList(
                "SELECT id FROM usuario WHERE rol = 'ADMIN' AND activo = 1", Long.class);
        List<Long> creados = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // Deja exactamente dos ADMIN activos: los del escenario.
            jdbc.update("UPDATE usuario SET activo = 0 WHERE rol = 'ADMIN' AND activo = 1");
            Usuario a = usuarios.saveAndFlush(new Usuario("Admin A", "carrera.a." + sufijo + "@livorno.test", "hash",
                    Rol.ADMIN));
            Usuario b = usuarios.saveAndFlush(new Usuario("Admin B", "carrera.b." + sufijo + "@livorno.test", "hash",
                    Rol.ADMIN));
            creados.add(a.getId());
            creados.add(b.getId());

            CountDownLatch ambosConSnapshot = new CountDownLatch(2);
            Future<Throwable> deA = pool.submit(intento(a.getId(), b.getId(), accion, ambosConSnapshot));
            Future<Throwable> deB = pool.submit(intento(b.getId(), a.getId(), accion, ambosConSnapshot));
            List<Throwable> resultados = java.util.Arrays.asList(deA.get(60, TimeUnit.SECONDS), deB.get(60, TimeUnit.SECONDS));

            assertThat(resultados.stream().filter(r -> r == null)).hasSize(1);
            // El perdedor recibe 422 (regla de negocio) o, si InnoDB lo elige víctima de un deadlock, un fallo de
            // concurrencia (409 en la API); en ambos casos el invariante se conserva.
            assertThat(resultados.stream().filter(r -> r instanceof ExcepcionNegocio
                    || r instanceof org.springframework.dao.ConcurrencyFailureException)).hasSize(1);
            Integer activos = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuario WHERE rol = 'ADMIN' AND activo = 1 AND id IN (?, ?)",
                    Integer.class, a.getId(), b.getId());
            assertThat(activos).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            for (Long id : creados) {
                jdbc.update("DELETE FROM usuario WHERE id = ?", id);
            }
            for (Long id : adminsPrevios) {
                jdbc.update("UPDATE usuario SET activo = 1 WHERE id = ?", id);
            }
        }
    }

    /** Cada hilo abre su transacción, toma su snapshot con una lectura simple y espera al otro antes de actuar. */
    private Callable<Throwable> intento(Long quienActua, Long objetivo, Accion accion, CountDownLatch latch) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new UsuarioAutenticado(quienActua, "x@livorno.test", "X", "ADMIN"), null, List.of()));
            try {
                transaccion.executeWithoutResult(estado -> {
                    usuarios.findById(objetivo); // lectura consistente: fija el snapshot de la transacción
                    latch.countDown();
                    try {
                        if (!latch.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("El otro hilo no llegó a la barrera");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    if (accion == Accion.DESACTIVAR) {
                        servicio.cambiarEstado(objetivo, false);
                    } else {
                        Usuario u = usuarios.findById(objetivo).orElseThrow();
                        servicio.actualizar(objetivo, new co.livorno.controlstock.usuario.dto.ActualizarUsuarioRequest(
                                u.getNombre(), u.getEmail(), Rol.VENDEDOR));
                    }
                });
                return null;
            } catch (Throwable t) {
                return t;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
