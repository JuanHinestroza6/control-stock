package co.livorno.controlstock.comun.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Separada de ConfiguracionTiempo para no romper slices @WebMvcTest. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
public class ConfiguracionAuditoria {
}
