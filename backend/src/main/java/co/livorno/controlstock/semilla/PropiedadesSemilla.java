package co.livorno.controlstock.semilla;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del generador de datos semilla (ADR-008). {@code fechaFin} vacía = ayer; que sea anterior a hoy se
 * valida al generar, porque depende del reloj. {@code password} se valida al generar (política de contraseñas).
 */
@ConfigurationProperties("app.semilla")
public record PropiedadesSemilla(
        @DefaultValue("true") boolean ejecutar,
        @DefaultValue("true") boolean salirAlTerminar,
        @DefaultValue("42") long semillaAleatoria,
        @DefaultValue("24") int meses,
        LocalDate fechaFin,
        @DefaultValue("15") int ticketsDiaBase,
        String password) {

    public PropiedadesSemilla {
        if (meses < 1 || meses > 36) {
            throw new IllegalStateException("app.semilla.meses debe estar entre 1 y 36.");
        }
        if (ticketsDiaBase < 1 || ticketsDiaBase > 200) {
            throw new IllegalStateException("app.semilla.tickets-dia-base debe estar entre 1 y 200.");
        }
        if (password != null && password.isBlank()) {
            password = null;
        }
    }

    /** Enmascara la contraseña para que no aparezca en logs ni en mensajes. */
    @Override
    public String toString() {
        return "PropiedadesSemilla[ejecutar=" + ejecutar + ", salirAlTerminar=" + salirAlTerminar
                + ", semillaAleatoria=" + semillaAleatoria + ", meses=" + meses + ", fechaFin=" + fechaFin
                + ", ticketsDiaBase=" + ticketsDiaBase + ", password=" + (password == null ? "null" : "****") + "]";
    }
}
