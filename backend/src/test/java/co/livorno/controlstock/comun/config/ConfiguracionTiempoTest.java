package co.livorno.controlstock.comun.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class ConfiguracionTiempoTest {

    private final ConfiguracionTiempo config = new ConfiguracionTiempo();

    @Test
    void clock_porDefecto_usaZonaBogota() {
        assertThat(config.clock().getZone().getId()).isEqualTo("America/Bogota");
    }

    @Test
    void dateTimeProvider_conClockFijo_devuelveHoraLocalBogota() {
        // dado: 2026-10-01T15:30:00Z = 10:30 en Bogota (UTC-5)
        Clock fijo = Clock.fixed(Instant.parse("2026-10-01T15:30:00Z"), ConfiguracionTiempo.ZONA);
        DateTimeProvider provider = config.dateTimeProvider(fijo);

        // cuando
        Optional<TemporalAccessor> ahora = provider.getNow();

        // entonces
        assertThat(ahora).contains(LocalDateTime.of(2026, 10, 1, 10, 30));
    }
}
