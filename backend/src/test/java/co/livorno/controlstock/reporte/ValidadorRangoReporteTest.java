package co.livorno.controlstock.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.comun.excepcion.SolicitudInvalidaException;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte;
import co.livorno.controlstock.reporte.servicio.ValidadorRangoReporte.RangoReporte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ValidadorRangoReporteTest {

    // 2026-10-21 15:00 en Bogotá
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-21T20:00:00Z"), ZoneId.of("America/Bogota"));
    private final ValidadorRangoReporte validador = new ValidadorRangoReporte(reloj);

    @Test
    void validar_sinFechas_usaElDiaUnoDelMesHastaHoy() {
        RangoReporte r = validador.validar(null, null);

        assertThat(r.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.hasta()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void validar_soloHasta_desdeEsElDiaUnoDeSuMes() {
        RangoReporte r = validador.validar(null, LocalDate.of(2026, 8, 15));

        assertThat(r.desde()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void validar_rangoDe731Dias_esValido() {
        LocalDate hasta = LocalDate.of(2026, 10, 21);

        RangoReporte r = validador.validar(hasta.minusDays(730), hasta);

        assertThat(r.desde()).isEqualTo(hasta.minusDays(730));
    }

    @Test
    void validar_rangoDe732Dias_responde400() {
        LocalDate hasta = LocalDate.of(2026, 10, 21);

        assertThatThrownBy(() -> validador.validar(hasta.minusDays(731), hasta))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("731");
    }

    @Test
    void validar_desdePosteriorAHasta_responde400() {
        assertThatThrownBy(() -> validador.validar(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void validar_hastaFutura_responde400() {
        assertThatThrownBy(() -> validador.validar(null, LocalDate.of(2026, 10, 22)))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("futura");
    }

    @Test
    void validar_desdeFuturoSinHasta_responde400() {
        assertThatThrownBy(() -> validador.validar(LocalDate.of(2026, 10, 22), null))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void validar_hoyComoHasta_esValido() {
        assertThat(validador.validar(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21)).hasta())
                .isEqualTo(LocalDate.of(2026, 10, 21));
    }
}
