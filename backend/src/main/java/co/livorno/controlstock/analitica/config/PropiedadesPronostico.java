package co.livorno.controlstock.analitica.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del pronóstico de demanda (ADR-010). Los valores por defecto están también en {@code application.yml}.
 * {@code mesesHistoria}: máximo de meses completos de la serie; {@code disponibilidadMinima}: piso de la
 * disponibilidad al corregir por agotados; {@code ventanaMezclaDias}: ventana de la participación de cada variante;
 * {@code horizonte}: meses pronosticados (el actual y los siguientes); {@code mesesBacktest}: meses de evaluación.
 */
@ConfigurationProperties("app.pronostico")
public record PropiedadesPronostico(
        @DefaultValue("36") int mesesHistoria,
        @DefaultValue("0.5") BigDecimal disponibilidadMinima,
        @DefaultValue("180") int ventanaMezclaDias,
        @DefaultValue("3") int horizonte,
        @DefaultValue("6") int mesesBacktest) {

    public PropiedadesPronostico {
        if (mesesHistoria < 3 || mesesHistoria > 120) {
            throw new IllegalStateException("app.pronostico.meses-historia debe estar entre 3 y 120.");
        }
        if (disponibilidadMinima == null || disponibilidadMinima.signum() <= 0
                || disponibilidadMinima.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("app.pronostico.disponibilidad-minima debe estar en (0, 1].");
        }
        if (ventanaMezclaDias < 1 || ventanaMezclaDias > 730) {
            throw new IllegalStateException("app.pronostico.ventana-mezcla-dias debe estar entre 1 y 730.");
        }
        if (horizonte < 1 || horizonte > 12) {
            throw new IllegalStateException("app.pronostico.horizonte debe estar entre 1 y 12.");
        }
        if (mesesBacktest < 1 || mesesBacktest > mesesHistoria) {
            throw new IllegalStateException("app.pronostico.meses-backtest debe estar entre 1 y los meses de historia.");
        }
    }
}
