package co.livorno.controlstock.analitica.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parametros de la analitica (ADR-009). Los valores por defecto estan en {@code application.yml}; el cron se apaga
 * con {@code app.tareas.habilitadas=false}. Nivel de servicio y z por clase: A 95 % / 1,65, B 90 % / 1,28,
 * C 80 % / 0,84 (la clase null usa los de C).
 */
@ConfigurationProperties("app.analitica")
public record PropiedadesAnalitica(
        @DefaultValue("0 0 2 * * *") String cron,
        @DefaultValue("365") int ventanaAbcDias,
        @DefaultValue("90") int ventanaDemandaDias,
        @DefaultValue("0.80") BigDecimal corteA,
        @DefaultValue("0.95") BigDecimal corteB,
        @DefaultValue("30") int historiaMinimaDias,
        @DefaultValue("5") BigDecimal umbralPoisson,
        Clases clases) {

    /** Nivel de servicio y z de una clase. */
    public record ParametrosClase(BigDecimal nivelServicio, BigDecimal z) {
        public ParametrosClase {
            if (nivelServicio == null || nivelServicio.signum() <= 0 || nivelServicio.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalStateException("El nivel de servicio debe estar entre 0 y 1 (exclusivo).");
            }
            if (z == null || z.signum() < 0) {
                throw new IllegalStateException("z no puede ser negativo.");
            }
        }
    }

    public record Clases(ParametrosClase a, ParametrosClase b, ParametrosClase c) {
        public Clases {
            a = a != null ? a : new ParametrosClase(new BigDecimal("0.95"), new BigDecimal("1.65"));
            b = b != null ? b : new ParametrosClase(new BigDecimal("0.90"), new BigDecimal("1.28"));
            c = c != null ? c : new ParametrosClase(new BigDecimal("0.80"), new BigDecimal("0.84"));
        }
    }

    public PropiedadesAnalitica {
        if (cron == null || cron.isBlank()) {
            throw new IllegalStateException("app.analitica.cron es obligatorio.");
        }
        if (ventanaAbcDias < 1 || ventanaAbcDias > 1100) {
            throw new IllegalStateException("app.analitica.ventana-abc-dias debe estar entre 1 y 1100.");
        }
        if (ventanaDemandaDias < 2 || ventanaDemandaDias > 365) {
            throw new IllegalStateException("app.analitica.ventana-demanda-dias debe estar entre 2 y 365.");
        }
        if (corteA == null || corteB == null || corteA.signum() <= 0 || corteA.compareTo(corteB) >= 0
                || corteB.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException("Los cortes ABC deben cumplir 0 < A < B < 1.");
        }
        if (historiaMinimaDias < 1 || historiaMinimaDias > ventanaDemandaDias) {
            throw new IllegalStateException(
                    "app.analitica.historia-minima-dias debe estar entre 1 y la ventana de demanda.");
        }
        if (umbralPoisson == null || umbralPoisson.signum() <= 0) {
            throw new IllegalStateException("app.analitica.umbral-poisson debe ser positivo.");
        }
        clases = clases != null ? clases : new Clases(null, null, null);
    }

    /** Parametros de la clase 'A', 'B' o 'C'; cualquier otro valor (incluido null) usa los de C. */
    public ParametrosClase parametrosDe(Character clase) {
        if (clase == null) {
            return clases.c();
        }
        return switch (clase) {
            case 'A' -> clases.a();
            case 'B' -> clases.b();
            default -> clases.c();
        };
    }
}
