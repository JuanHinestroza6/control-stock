package co.livorno.controlstock.comun.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

@Configuration
public class ConfiguracionTiempo {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA);
    }

    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
