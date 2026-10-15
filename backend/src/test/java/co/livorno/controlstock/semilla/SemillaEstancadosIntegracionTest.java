package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Los productos estancados (LIV004, LIV026, LIV030) no tienen ninguna venta en los últimos 90 días de la historia y
 * conservan stock, así que la demo muestra SIN_ROTACION (ADR-009). Seis meses de historia (julio a diciembre de 2025)
 * para que la ventana de 90 días quepa con ventas previas. Reloj en fecha-fin + 1, como la carga real.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "seed"})
@SpringBootTest(properties = {
        "app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false", "app.semilla.meses=6",
        "app.semilla.tickets-dia-base=6", "app.semilla.fecha-fin=2025-12-31",
        "app.semilla.password=ClaveDemo-2026-ok"})
class SemillaEstancadosIntegracionTest {

    private static final LocalDate FIN = LocalDate.of(2025, 12, 31);
    private static final List<String> ESTANCADOS = List.of("LIV004", "LIV026", "LIV030");
    private static final String IN_ESTANCADOS = "('LIV004', 'LIV026', 'LIV030')";

    @Autowired
    GeneradorDatosSemilla generador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojSimulado reloj;

    @BeforeEach
    void prepararBaseYReloj() {
        LimpiezaSemilla.limpiar(jdbc);
        LimpiezaSemilla.fijarCierreTrasFin(reloj, FIN);
    }

    @AfterEach
    void limpiarYRestablecerReloj() {
        try {
            LimpiezaSemilla.limpiar(jdbc);
        } finally {
            LimpiezaSemilla.restablecerReloj(reloj);
        }
    }

    private long contar(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void generar_estancados_sinVentasNiReservasEnLos90DiasFinales_yConVentasAntes() {
        generador.generar();
        LocalDate corte = FIN.plusDays(1).minusDays(90);

        // dado la carga completa, cuando se miran los movimientos de venta de los estancados en los últimos 90 días
        long ventasRecientes = contar("""
                select count(*) from movimiento m join variante v on v.id = m.variante_id
                join producto p on p.id = v.producto_id
                where p.codigo in %s and m.tipo in ('VENTA', 'VENTA_RESERVA') and m.fecha >= ?""".formatted(IN_ESTANCADOS),
                corte.atStartOfDay());
        long reservasRecientes = contar("""
                select count(*) from reserva r join variante v on v.id = r.variante_id
                join producto p on p.id = v.producto_id
                where p.codigo in %s and r.creada_en >= ?""".formatted(IN_ESTANCADOS), corte.atStartOfDay());
        // entonces no hay ninguna, pero sí hubo ventas antes de la ventana en cada uno
        assertThat(ventasRecientes).isZero();
        assertThat(reservasRecientes).isZero();
        for (String codigo : ESTANCADOS) {
            assertThat(contar("""
                    select count(*) from movimiento m join variante v on v.id = m.variante_id
                    join producto p on p.id = v.producto_id
                    where p.codigo = ? and m.tipo = 'VENTA' and m.fecha < ?""", codigo, corte.atStartOfDay()))
                    .as("ventas previas de %s", codigo).isPositive();
        }
    }

    @Test
    void generar_estancados_variantesConDisponibleTienenSinRotacionActiva() {
        ResumenSemilla r = generador.generar();

        // variantes activas de los estancados con disponible > 0 y su alerta ACTIVA
        long conDisponible = contar("""
                select count(*) from variante v join producto p on p.id = v.producto_id
                where p.codigo in %s and v.activo = 1 and v.stock_fisico - v.stock_reservado > 0"""
                .formatted(IN_ESTANCADOS));
        long conSinRotacion = contar("""
                select count(*) from variante v join producto p on p.id = v.producto_id
                join alerta a on a.variante_id = v.id and a.estado = 'ACTIVA' and a.tipo = 'SIN_ROTACION'
                where p.codigo in %s and v.activo = 1 and v.stock_fisico - v.stock_reservado > 0"""
                .formatted(IN_ESTANCADOS));
        assertThat(conDisponible).isPositive();
        assertThat(conSinRotacion).isEqualTo(conDisponible);
        for (String codigo : ESTANCADOS) {
            assertThat(contar("""
                    select count(*) from variante v join producto p on p.id = v.producto_id
                    where p.codigo = ? and v.stock_fisico - v.stock_reservado > 0""", codigo))
                    .as("variantes con stock de %s", codigo).isPositive();
        }

        // la métrica de la ejecución SEMILLA coincide con el conteo SQL (y con el resumen)
        long sqlSinRotacion = contar("select count(*) from alerta where estado = 'ACTIVA' and tipo = 'SIN_ROTACION'");
        assertThat(jdbc.queryForObject("""
                select sin_rotacion_activas from ejecucion_analitica where origen = 'SEMILLA'""", Long.class))
                .isEqualTo(sqlSinRotacion);
        assertThat(r.analitica().alertasSinRotacion()).isEqualTo(sqlSinRotacion);
        assertThat(sqlSinRotacion).isGreaterThanOrEqualTo(conSinRotacion);
    }
}
