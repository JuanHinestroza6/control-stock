package co.livorno.controlstock.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Limitador de intentos fallidos de login: maxIntentos fallos dentro de una ventana bloquean la clave. */
@ConfigurationProperties("app.login")
public record PropiedadesLogin(@DefaultValue("5") int maxIntentos, @DefaultValue("15m") Duration ventana) {

    public PropiedadesLogin {
        if (maxIntentos < 1) {
            throw new IllegalStateException("app.login.max-intentos debe ser al menos 1.");
        }
        if (ventana == null || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalStateException("app.login.ventana debe ser una duracion positiva.");
        }
    }
}
