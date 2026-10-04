package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B4 (ADR-008): simulación diaria con volumen reducido (3 meses, enero incluido). Cada prueba hace rollback. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "seed"})
@SpringBootTest(properties = {
        "app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false", "app.semilla.meses=3",
        "app.semilla.tickets-dia-base=6", "app.semilla.fecha-fin=2026-02-15",
        "app.semilla.password=ClaveDemo-2026-ok"})
@Transactional
class SimulacionIntegracionTest {

    private static final String INICIO = "2025-11-16 00:00:00";
    private static final String LIMITE = "2026-02-15 23:59:59.999999";

    @Autowired
    GeneradorDatosSemilla generador;
    @Autowired
    JdbcTemplate jdbc;

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void generar_simulaOperacionesYConservaLosInvariantes() {
        // cuando
        ResumenSemilla resumen = generador.generar();
        ResultadoSimulacion r = resumen.simulacion();

        // entonces: hubo de todo (semilla fija, así que es reproducible)
        assertThat(resumen.inicio()).isEqualTo(LocalDate.of(2025, 11, 16));
        assertThat(r.dias()).isEqualTo(92);
        assertThat(r.ventas()).isGreaterThan(300);
        assertThat(r.entradas()).isPositive();
        assertThat(r.devoluciones()).isPositive();
        assertThat(r.ajustes()).isPositive();
        assertThat(r.alzasDePrecio()).isEqualTo(1);
        assertThat(r.anuladas()).isPositive();
        assertThat(r.unidadesPerdidas()).isGreaterThanOrEqualTo(0);

        // stock = suma del kardex; reservado = 0; nada negativo
        assertThat(contar("""
                select count(*) from variante v where v.stock_fisico <>
                (select coalesce(sum(m.fisico_despues - m.fisico_antes), 0) from movimiento m where m.variante_id = v.id)
                or v.stock_reservado <> 0 or v.stock_fisico < 0""")).isZero();
        assertThat(contar("select count(*) from movimiento where fisico_despues < 0 or reservado_despues < 0"))
                .isZero();

        // kardex encadenado por variante y fechas no decrecientes por id
        assertThat(contar("""
                select count(*) from (select fisico_antes, lag(fisico_despues) over (partition by variante_id
                order by id) as previo from movimiento) t where previo is not null and fisico_antes <> previo"""))
                .isZero();
        assertThat(contar("""
                select count(*) from (select fecha, lag(fecha) over (order by id) as previa from movimiento) t
                where previa is not null and fecha < previa""")).isZero();

        // fechas dentro de [inicio, fin 23:59]
        assertThat(contar("select count(*) from movimiento where fecha < '" + INICIO + "' or fecha > '" + LIMITE + "'"))
                .isZero();
        assertThat(contar("select count(*) from venta where fecha < '" + INICIO + "' or fecha > '" + LIMITE + "'"))
                .isZero();
        assertThat(contar("select count(*) from devolucion where fecha < '" + INICIO + "' or fecha > '" + LIMITE
                + "'")).isZero();

        // ventas: total = suma de líneas, hay descuentos, la anulada repone una vez por línea
        assertThat(contar("""
                select count(*) from venta v where v.total <>
                (select sum(d.cantidad * d.precio_unitario) from detalle_venta d where d.venta_id = v.id)"""))
                .isZero();
        assertThat(contar("select count(*) from detalle_venta where precio_unitario < precio_lista")).isPositive();
        assertThat(contar("select count(*) from venta where estado = 'ANULADA'")).isEqualTo(r.anuladas());
        assertThat(contar("""
                select count(*) from venta v where v.estado = 'ANULADA' and
                (select count(*) from movimiento m where m.tipo = 'ANULACION_VENTA' and m.referencia_id = v.id)
                <> (select count(*) from detalle_venta d where d.venta_id = v.id)""")).isZero();
        assertThat(contar("""
                select count(*) from devolucion dv join detalle_venta d on d.id = dv.detalle_venta_id
                join venta v on v.id = d.venta_id where v.estado = 'ANULADA'""")).isZero();
        assertThat(contar("""
                select count(*) from detalle_venta d where d.cantidad <
                (select coalesce(sum(dv.cantidad), 0) from devolucion dv where dv.detalle_venta_id = d.id)"""))
                .isZero();

        // el ingreso del resumen coincide con la base (sin anuladas)
        BigDecimal ingresoBd = jdbc.queryForObject(
                "select coalesce(sum(total), 0) from venta where estado = 'REGISTRADA'", BigDecimal.class);
        assertThat(r.ingreso()).isEqualByComparingTo(ingresoBd);

        // alza de enero: terminación 900 en los precios de venta
        assertThat(contar("select count(*) from producto where mod(precio_venta, 1000) <> 900")).isZero();
    }

    @Test
    void alzaConTerminacion900_sumaCincoPorCientoYTerminaEn900() {
        assertThat(SimuladorDiario.alzaConTerminacion900(new BigDecimal("79900.00")))
                .isEqualByComparingTo("83900");
        assertThat(SimuladorDiario.alzaConTerminacion900(new BigDecimal("80000.00")))
                .isEqualByComparingTo("83900");
        assertThat(SimuladorDiario.alzaConTerminacion900(new BigDecimal("19900.00")))
                .isEqualByComparingTo("20900");
    }
}
