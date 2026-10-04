package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/** B3 (ADR-008): usuarios demo, catálogo y entrada inicial con los servicios reales. Cada prueba hace rollback. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "seed"})
@SpringBootTest(properties = {
        "app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false", "app.semilla.meses=3", "app.semilla.tickets-dia-base=2",
        "app.semilla.fecha-fin=2025-12-31", "app.semilla.password=ClaveDemo-2026-ok"})
@Transactional
class GeneradorCatalogoIntegracionTest {

    @Autowired
    GeneradorDatosSemilla generador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojSimulado reloj;

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void generar_baseVacia_creaUsuariosCatalogoYEntradaInicial() {
        // cuando
        ResumenSemilla resumen = generador.generar();

        // entonces: resumen y reloj devuelto al sistema
        assertThat(resumen.generado()).isTrue();
        assertThat(resumen.inicio()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(resumen.fin()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(reloj.estaSimulando()).isFalse();

        // catálogo: 30 productos, variantes con stockMinimo >= 2, ninguna en 0 de stock
        assertThat(contar("select count(*) from producto")).isEqualTo(30);
        assertThat(contar("select count(*) from variante")).isEqualTo(resumen.variantes()).isGreaterThan(200);
        assertThat(contar("select count(*) from variante where stock_minimo < 2")).isZero();
        assertThat(contar("select count(*) from variante where stock_reservado <> 0")).isZero();

        // kardex: una ENTRADA inicial por variante con costo, fechada en el día 0 a las 08:00
        String inicial = "from movimiento where observacion = 'Stock inicial'";
        assertThat(contar("select count(*) " + inicial)).isEqualTo(resumen.variantes());
        assertThat(contar("select count(*) " + inicial + " and (tipo <> 'ENTRADA' or costo_unitario is null)"))
                .isZero();
        assertThat(contar("select count(*) " + inicial + " and fecha <> '2025-10-01 08:00:00'")).isZero();
        assertThat(contar("select coalesce(sum(cantidad),0) " + inicial)).isEqualTo(resumen.unidadesIniciales());

        assertThat(contar("select count(*) from producto where creado_en <> '2025-10-01 07:00:00'")).isZero();
    }

    @Test
    void generar_baseVacia_creaUnUsuarioPorRolSinCambioForzado() {
        generador.generar();

        List<Map<String, Object>> demo = jdbc.queryForList("""
                select rol, debe_cambiar_password from usuario where email like '%@livorno.co' order by rol""");
        assertThat(demo).extracting(f -> f.get("rol")).containsExactlyInAnyOrder("ADMIN", "INVENTARIO",
                "VENDEDOR", "CONTABILIDAD");
        assertThat(demo).extracting(f -> f.get("debe_cambiar_password")).containsOnly(false);
    }

    @Test
    void generar_usuarioDemoExistente_loReutiliza() {
        jdbc.update("""
                insert into usuario (nombre, email, password_hash, rol, activo, debe_cambiar_password, creado_en,
                actualizado_en) values ('Previo', 'ventas@livorno.co', 'hash-previo', 'VENDEDOR', 1, 0, now(), now())""");

        generador.generar();

        assertThat(contar("select count(*) from usuario where email = 'ventas@livorno.co'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from usuario where email = 'ventas@livorno.co'",
                String.class)).isEqualTo("hash-previo");
    }

    @Test
    void generar_segundaVezConVentas_noHaceNada() {
        generador.generar();
        long movimientos = contar("select count(*) from movimiento");

        ResumenSemilla segunda = generador.generar();

        assertThat(segunda.generado()).isFalse();
        assertThat(contar("select count(*) from movimiento")).isEqualTo(movimientos);
        assertThat(contar("select count(*) from producto")).isEqualTo(30);
    }

    @Test
    void generar_conProductosSinVentas_abortaPorDatosParciales() {
        generador.generar();
        jdbc.update("delete from devolucion");
        jdbc.update("delete from detalle_venta");
        jdbc.update("delete from venta");

        assertThatThrownBy(generador::generar).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("docker compose down -v");
    }
}
