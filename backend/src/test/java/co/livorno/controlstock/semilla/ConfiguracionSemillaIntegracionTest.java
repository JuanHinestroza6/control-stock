package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.inventario.servicio.AlertaListener;
import java.time.Clock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B1 (ADR-008): el reloj simulado y la desactivación del listener solo existen con el perfil seed. */
@Testcontainers(disabledWithoutDocker = true)
class ConfiguracionSemillaIntegracionTest {

    @Nested
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("test")
    @SpringBootTest
    class SinPerfilSeed {

        @Autowired
        ApplicationContext contexto;

        @Test
        void contexto_sinSeed_usaRelojDelSistemaYRegistraElListener() {
            assertThat(contexto.getBean(Clock.class)).isNotInstanceOf(RelojSimulado.class);
            assertThat(contexto.getBeanNamesForType(RelojSimulado.class)).isEmpty();
            assertThat(contexto.getBeanNamesForType(PropiedadesSemilla.class)).isEmpty();
            assertThat(contexto.getBeanNamesForType(AlertaListener.class)).hasSize(1);
        }
    }

    @Nested
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles({"test", "seed"})
    @SpringBootTest(properties = {"app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false"})
    class ConPerfilSeed {

        @Autowired
        ApplicationContext contexto;

        @Test
        void contexto_conSeed_usaRelojSimuladoSinListenerDeAlertas() {
            assertThat(contexto.getBean(Clock.class)).isInstanceOf(RelojSimulado.class);
            assertThat(contexto.getBeanNamesForType(AlertaListener.class)).isEmpty();
        }

        @Test
        void propiedades_conSeed_tomanLosValoresDelYml() {
            PropiedadesSemilla p = contexto.getBean(PropiedadesSemilla.class);

            assertThat(p.ejecutar()).isFalse();
            assertThat(p.salirAlTerminar()).isFalse();
            assertThat(p.semillaAleatoria()).isEqualTo(42L);
            assertThat(p.meses()).isEqualTo(36);
            assertThat(p.fechaFin()).isNull();
            assertThat(p.ticketsDiaBase()).isEqualTo(15);
        }
    }
}
