package co.livorno.controlstock.reporte.servicio;

import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Valida y completa el rango de fechas de los reportes (ADR-011, R3 y R4): por defecto del día 1 del mes hasta hoy
 * (hora del {@code Clock}, America/Bogota); inclusivo, máximo 731 días; {@code desde > hasta} o {@code hasta} futura
 * responden 400. Si solo se indica {@code hasta}, el {@code desde} por defecto es el día 1 de su mes.
 */
@Component
public class ValidadorRangoReporte {

    public static final int RANGO_MAXIMO_DIAS = 731;

    /** Rango efectivo, inclusivo en ambos extremos. */
    public record RangoReporte(LocalDate desde, LocalDate hasta) {}

    private final Clock clock;

    public ValidadorRangoReporte(Clock clock) {
        this.clock = clock;
    }

    public RangoReporte validar(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate inicio = desde != null ? desde : fin.withDayOfMonth(1);
        if (fin.isAfter(hoy)) {
            throw new SolicitudInvalidaException("hasta", "La fecha final no puede ser futura.");
        }
        if (inicio.isAfter(fin)) {
            throw new SolicitudInvalidaException("desde", "La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > RANGO_MAXIMO_DIAS) {
            throw new SolicitudInvalidaException("desde", "El rango no puede superar " + RANGO_MAXIMO_DIAS + " días.");
        }
        return new RangoReporte(inicio, fin);
    }
}
