package co.livorno.controlstock.semilla;

import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.venta.repositorio.VentaRepository;
import java.time.LocalDate;
import java.util.Arrays;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Guardas de la carga (ADR-008, D5 y D7): perfil permitido (dev o test), fecha final y estado de la base. Solo lectura. */
@Component
@Profile("seed")
public class ProteccionesSemilla {

    public static final String MENSAJE_BASE_NO_VACIA = "base no vacía: docker compose down -v";

    /** Qué hacer según el estado de la base. */
    public enum Estado { VACIA, YA_HAY_DATOS }

    private final Environment entorno;
    private final ProductoRepository productos;
    private final VentaRepository ventas;
    private final JdbcTemplate jdbc;

    public ProteccionesSemilla(Environment entorno, ProductoRepository productos, VentaRepository ventas,
                               JdbcTemplate jdbc) {
        this.entorno = entorno;
        this.productos = productos;
        this.ventas = ventas;
        this.jdbc = jdbc;
    }

    /** Lista de permitidos: solo corre si el entorno acepta {@code dev} o {@code test}; cualquier otro caso aborta. */
    public void validarPerfil() {
        if (!entorno.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new SemillaAbortadaException("El generador de datos semilla solo corre con el perfil dev (o test); "
                    + "perfiles activos: " + Arrays.toString(entorno.getActiveProfiles())
                    + ". Usa -Dspring-boot.run.profiles=dev,seed.");
        }
    }

    /** La fecha final debe ser anterior a hoy. */
    public void validarFechaFin(LocalDate fechaFin, LocalDate hoy) {
        if (fechaFin == null || !fechaFin.isBefore(hoy)) {
            throw new SemillaAbortadaException("app.semilla.fecha-fin (" + fechaFin
                    + ") debe ser anterior a hoy (" + hoy + ").");
        }
    }

    /**
     * Con ventas: {@code YA_HAY_DATOS} (no se hace nada). Con productos o movimientos pero sin ventas (carga parcial
     * o datos manuales): aborta. Sin nada: {@code VACIA}.
     */
    public Estado evaluarBase() {
        if (ventas.count() > 0) {
            return Estado.YA_HAY_DATOS;
        }
        Long movimientos = jdbc.queryForObject("select count(*) from movimiento", Long.class);
        if (productos.count() > 0 || (movimientos != null && movimientos > 0)) {
            throw new SemillaAbortadaException(MENSAJE_BASE_NO_VACIA);
        }
        return Estado.VACIA;
    }
}
