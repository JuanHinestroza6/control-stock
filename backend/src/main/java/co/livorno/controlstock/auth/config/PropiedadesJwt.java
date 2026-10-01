package co.livorno.controlstock.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Propiedades app.jwt.*. La aplicacion no arranca si el secreto falta o mide menos de 32 bytes (HS256). */
@Validated
@ConfigurationProperties("app.jwt")
public record PropiedadesJwt(String secret, @DefaultValue("8h") Duration expiracion) {

    public static final int MIN_BYTES_SECRETO = 32;

    public PropiedadesJwt {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException("JWT_SECRET (app.jwt.secret) es obligatorio y debe medir al menos "
                    + MIN_BYTES_SECRETO + " bytes (UTF-8).");
        }
        if (secret.toLowerCase(java.util.Locale.ROOT).startsWith("cambiar")) {
            throw new IllegalStateException("JWT_SECRET (app.jwt.secret) conserva el valor de ejemplo de .env.example; "
                    + "genera uno aleatorio (p. ej. openssl rand -base64 48).");
        }
        if (expiracion == null || expiracion.isZero() || expiracion.isNegative()) {
            throw new IllegalStateException("app.jwt.expiracion debe ser una duracion positiva.");
        }
    }
}
