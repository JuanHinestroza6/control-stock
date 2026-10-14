package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B6 (ADR-008): sin SEED_PASSWORD la carga falla con mensaje claro y la base queda como estaba (rollback total). */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "seed"})
@SpringBootTest(properties = {
        "app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false", "app.semilla.meses=3",
        "app.semilla.tickets-dia-base=2", "app.semilla.fecha-fin=2025-12-31", "app.semilla.password="})
class SemillaSinPasswordIntegracionTest {

    @Autowired
    GeneradorDatosSemilla generador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojSimulado reloj;

    @BeforeEach
    @AfterEach
    void limpiar() {
        LimpiezaSemilla.limpiar(jdbc);
    }

    @Test
    void generar_sinSeedPassword_fallaConMensajeClaroYNoDejaFilas() {
        assertThatThrownBy(generador::generar).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("SEED_PASSWORD");

        for (String tabla : new String[] {"producto", "variante", "movimiento", "venta", "reserva", "alerta"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabla, Long.class)).as(tabla).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from usuario where email like '%@livorno.co'", Long.class))
                .isZero();
        assertThat(reloj.estaSimulando()).isFalse();
    }
}
