package co.livorno.controlstock.auth.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.auth.config.PropiedadesLogin;
import co.livorno.controlstock.comun.excepcion.DemasiadosIntentosException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LimitadorIntentosLoginTest {

    /** Reloj mutable para avanzar el tiempo en las pruebas. */
    static class RelojMutable extends Clock {
        Instant ahora = Instant.parse("2026-10-01T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("America/Bogota"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    private final RelojMutable reloj = new RelojMutable();
    private final LimitadorIntentosLogin limitador =
            new LimitadorIntentosLogin(new PropiedadesLogin(5, Duration.ofMinutes(15)), reloj);

    private void fallar(String email, String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrarFallo(email, ip);
        }
    }

    @Test
    void verificar_cuatroFallos_noBloquea() {
        fallar("a@x.test", "1.1.1.1", 4);

        assertThatCode(() -> limitador.verificarNoBloqueado("a@x.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void verificar_cincoFallos_bloqueaConSegundosRestantes() {
        fallar("a@x.test", "1.1.1.1", 5);
        reloj.avanzar(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limitador.verificarNoBloqueado("a@x.test", "1.1.1.1"))
                .isInstanceOfSatisfying(DemasiadosIntentosException.class,
                        e -> assertThat(e.getSegundosRestantes()).isEqualTo(600));
    }

    @Test
    void verificar_tras15Minutos_desbloquea() {
        fallar("a@x.test", "1.1.1.1", 5);
        reloj.avanzar(Duration.ofMinutes(15));

        assertThatCode(() -> limitador.verificarNoBloqueado("a@x.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void registrarFallo_tras15MinutosDelPrimero_reiniciaLaVentana() {
        fallar("a@x.test", "1.1.1.1", 4);
        reloj.avanzar(Duration.ofMinutes(16));
        fallar("a@x.test", "1.1.1.1", 1);

        assertThatCode(() -> limitador.verificarNoBloqueado("a@x.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void registrarExito_limpiaElContador() {
        fallar("a@x.test", "1.1.1.1", 4);
        limitador.registrarExito("a@x.test", "1.1.1.1");
        fallar("a@x.test", "1.1.1.1", 4);

        assertThatCode(() -> limitador.verificarNoBloqueado("a@x.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void verificar_clavesDistintasPorIpYEmail_sonIndependientes() {
        fallar("a@x.test", "1.1.1.1", 5);

        assertThatThrownBy(() -> limitador.verificarNoBloqueado("A@X.test ", "1.1.1.1"))
                .isInstanceOf(DemasiadosIntentosException.class); // email normalizado
        assertThatCode(() -> limitador.verificarNoBloqueado("a@x.test", "2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> limitador.verificarNoBloqueado("b@x.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void registrarFallo_limpiaEntradasVencidas() {
        fallar("a@x.test", "1.1.1.1", 1);
        fallar("b@x.test", "1.1.1.1", 1);
        reloj.avanzar(Duration.ofMinutes(20));

        fallar("c@x.test", "1.1.1.1", 1);

        assertThat(limitador.tamano()).isEqualTo(1);
    }
}
