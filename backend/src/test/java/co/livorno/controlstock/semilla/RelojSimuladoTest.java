package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RelojSimuladoTest {

    @Test
    void instant_sinAvanzar_usaElRelojDelSistema() {
        // dado un reloj nuevo
        RelojSimulado reloj = new RelojSimulado();
        // cuando
        Instant antes = Instant.now();
        Instant leido = reloj.instant();
        Instant despues = Instant.now();
        // entonces
        assertThat(leido).isBetween(antes, despues);
        assertThat(reloj.estaSimulando()).isFalse();
        assertThat(reloj.getZone()).isEqualTo(ConfiguracionTiempo.ZONA);
    }

    @Test
    void avanzarA_fijaLaHoraSimuladaEnBogota() {
        RelojSimulado reloj = new RelojSimulado();
        LocalDateTime momento = LocalDateTime.of(2024, 3, 15, 10, 30);

        reloj.avanzarA(momento);

        assertThat(LocalDateTime.now(reloj)).isEqualTo(momento);
        assertThat(reloj.instant()).isEqualTo(momento.atZone(ConfiguracionTiempo.ZONA).toInstant());
        assertThat(reloj.estaSimulando()).isTrue();
    }

    @Test
    void avanzarA_mismaHoraOPosterior_esValido() {
        RelojSimulado reloj = new RelojSimulado();
        LocalDateTime momento = LocalDateTime.of(2024, 3, 15, 10, 30);
        reloj.avanzarA(momento);

        reloj.avanzarA(momento);
        reloj.avanzarA(momento.plusHours(5));

        assertThat(LocalDateTime.now(reloj)).isEqualTo(momento.plusHours(5));
    }

    @Test
    void avanzarA_anteriorALaActual_lanzaIllegalState() {
        RelojSimulado reloj = new RelojSimulado();
        LocalDateTime momento = LocalDateTime.of(2024, 3, 15, 10, 30);
        reloj.avanzarA(momento);

        assertThatThrownBy(() -> reloj.avanzarA(momento.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no puede retroceder");
        assertThat(LocalDateTime.now(reloj)).isEqualTo(momento);
    }

    @Test
    void volverAlSistema_dejaDeSimularYPermiteReiniciar() {
        RelojSimulado reloj = new RelojSimulado();
        reloj.avanzarA(LocalDateTime.of(2024, 3, 15, 10, 30));

        reloj.volverAlSistema();

        assertThat(reloj.estaSimulando()).isFalse();
        assertThat(LocalDateTime.now(reloj).getYear()).isGreaterThanOrEqualTo(2026);
        // tras volver, una fecha pasada vuelve a ser aceptada
        reloj.avanzarA(LocalDateTime.of(2020, 1, 1, 8, 0));
        assertThat(LocalDateTime.now(reloj)).isEqualTo(LocalDateTime.of(2020, 1, 1, 8, 0));
    }
}
