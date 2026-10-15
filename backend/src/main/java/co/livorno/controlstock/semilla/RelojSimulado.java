package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reloj del generador de datos semilla (ADR-008). Mientras no se avance se comporta como el reloj del sistema; tras
 * {@link #avanzarA(LocalDateTime)} devuelve esa hora fija. Solo avanza, para que el kardex quede cronológico.
 * Pensado para un solo hilo (la carga).
 */
@Component
@Primary
@Profile("seed")
public class RelojSimulado extends Clock {

    private volatile Clock sistema = Clock.system(ConfiguracionTiempo.ZONA);
    private volatile LocalDateTime simulado;

    /** Fija la hora simulada; no puede ser anterior a la simulada actual. */
    public void avanzarA(LocalDateTime momento) {
        if (momento == null) {
            throw new IllegalArgumentException("El momento simulado es obligatorio.");
        }
        LocalDateTime actual = simulado;
        if (actual != null && momento.isBefore(actual)) {
            throw new IllegalStateException(
                    "El reloj simulado no puede retroceder: actual " + actual + ", solicitado " + momento + ".");
        }
        simulado = momento;
    }

    /**
     * Solo pruebas: reemplaza el reloj del sistema al que se vuelve tras la carga (p. ej. uno fijo en
     * {@code fecha-fin + 1}, para que la analítica de cierre vea la historia cerca del corte). Con {@code null}
     * restablece el reloj real.
     */
    void sustituirRelojDelSistema(Clock reloj) {
        sistema = reloj == null ? Clock.system(ConfiguracionTiempo.ZONA) : reloj;
    }

    /** Vuelve al reloj del sistema. */
    public void volverAlSistema() {
        simulado = null;
    }

    public boolean estaSimulando() {
        return simulado != null;
    }

    @Override
    public ZoneId getZone() {
        return ConfiguracionTiempo.ZONA;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        if (ConfiguracionTiempo.ZONA.equals(zona)) {
            return this;
        }
        throw new UnsupportedOperationException("El reloj simulado solo opera en " + ConfiguracionTiempo.ZONA);
    }

    @Override
    public Instant instant() {
        LocalDateTime actual = simulado;
        return actual == null ? sistema.instant() : actual.atZone(ConfiguracionTiempo.ZONA).toInstant();
    }
}
