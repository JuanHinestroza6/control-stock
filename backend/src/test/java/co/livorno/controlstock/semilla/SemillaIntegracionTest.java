package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.servicio.AuthService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * B6 (ADR-008): invariantes de la semilla que no cubren las demás pruebas del módulo (idempotencia total, rollback,
 * login de los usuarios demo, alertas vs. stock, estacionalidad y determinismo). Sin @Transactional: la carga abre su
 * propia transacción y cada prueba limpia con {@link LimpiezaSemilla}. Octubre a diciembre de 2025.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"test", "seed"})
@SpringBootTest(properties = {
        "app.semilla.ejecutar=false", "app.semilla.salir-al-terminar=false", "app.semilla.meses=3",
        "app.semilla.tickets-dia-base=6", "app.semilla.fecha-fin=2025-12-31",
        "app.semilla.password=ClaveDemo-2026-ok"})
class SemillaIntegracionTest {

    private static final String[] TABLAS = {"usuario", "categoria", "producto", "variante", "movimiento", "venta",
            "detalle_venta", "devolucion", "reserva", "alerta"};

    @Autowired
    GeneradorDatosSemilla generador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AuthService authService;
    @Autowired
    RelojSimulado reloj;
    @MockitoSpyBean
    VerificadorConsistencia verificador;

    @BeforeEach
    @AfterEach
    void limpiar() {
        LimpiezaSemilla.limpiar(jdbc);
    }

    private long contar(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private Map<String, Long> conteos() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String t : TABLAS) {
            m.put(t, contar("select count(*) from " + t));
        }
        return m;
    }

    @Test
    void generar_segundaEjecucion_noCreaNingunaFilaEnNingunaTabla() {
        generador.generar();
        Map<String, Long> antes = conteos();

        ResumenSemilla segunda = generador.generar();

        assertThat(segunda.generado()).isFalse();
        assertThat(conteos()).isEqualTo(antes);
    }

    @Test
    void generar_errorForzadoAMitadDeCarga_revierteTodoYDevuelveElRelojAlSistema() {
        Map<String, Long> antes = conteos();
        doThrow(new SemillaAbortadaException("fallo forzado")).when(verificador).verificarCarga(any(), any());

        assertThatThrownBy(generador::generar).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("fallo forzado");

        // ya se habían escrito usuarios, catálogo y simulación dentro de la transacción: nada debe quedar
        assertThat(conteos()).isEqualTo(antes);
        assertThat(reloj.estaSimulando()).isFalse();
    }

    @Test
    void usuariosDemo_iniciaSesionConAuthServiceSinCambioForzado() {
        generador.generar();

        for (UsuariosDemo.Demo demo : UsuariosDemo.USUARIOS) {
            LoginResponse r = authService.login(new LoginRequest(demo.email(), "ClaveDemo-2026-ok"), "10.0.0.1");
            assertThat(r.token()).isNotBlank();
            assertThat(r.usuario().rol()).isEqualTo(demo.rol().name());
            assertThat(r.usuario().debeCambiarPassword()).as(demo.email()).isFalse();
        }
    }

    @Test
    void alertas_activaCoincideConElTipoVigenteDeCadaVarianteActiva() {
        generador.generar();

        // por variante activa: tipo de la alerta ACTIVA == tipoEsperado (AGOTADO / SIN_ROTACION / STOCK_BAJO / ninguna)
        verificador.verificarAlertas();
        // a lo sumo una ACTIVA por variante y tipo, y ninguna de variantes inactivas
        assertThat(contar("""
                select count(*) from (select variante_id, tipo from alerta where estado = 'ACTIVA'
                group by variante_id, tipo having count(*) > 1) t""")).isZero();
        assertThat(contar("""
                select count(*) from alerta a join variante v on v.id = a.variante_id
                where a.estado = 'ACTIVA' and v.activo = 0""")).isZero();
        // las atendidas automáticamente (sistema) no tienen usuario
        assertThat(contar("select count(*) from alerta where estado = 'ATENDIDA' and atendida_por is not null"))
                .isZero();
    }

    @Test
    void generar_unidadesVendidasDeDiciembre_superanALasDeOctubre() {
        generador.generar();

        long octubre = unidadesVendidasDelMes(10);
        long diciembre = unidadesVendidasDelMes(12);

        assertThat(octubre).isPositive();
        assertThat(diciembre).isGreaterThan(octubre);
    }

    private long unidadesVendidasDelMes(int mes) {
        return contar("select coalesce(sum(cantidad), 0) from movimiento where tipo in ('VENTA', 'VENTA_RESERVA') "
                + "and month(fecha) = " + mes);
    }

    @Test
    void generar_dosCorridasConLaMismaSemillaSobreBasesLimpias_danLosMismosTotales() {
        ResumenSemilla primera = generador.generar();
        Map<String, Object> huella1 = huella();
        Map<String, Long> filas1 = conteos();

        LimpiezaSemilla.limpiar(jdbc);
        ResumenSemilla segunda = generador.generar();

        assertThat(segunda.generado()).isTrue();
        assertThat(segunda.simulacion()).isEqualTo(primera.simulacion());
        assertThat(segunda.variantes()).isEqualTo(primera.variantes());
        assertThat(segunda.unidadesIniciales()).isEqualTo(primera.unidadesIniciales());
        assertThat(segunda.alertasAgotado()).isEqualTo(primera.alertasAgotado());
        assertThat(segunda.alertasStockBajo()).isEqualTo(primera.alertasStockBajo());
        assertThat(conteos()).isEqualTo(filas1);
        assertThat(huella()).isEqualTo(huella1);
    }

    /** Totales que dependen de todo el flujo aleatorio: stock final, ingreso y unidades por tipo de movimiento. */
    private Map<String, Object> huella() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("fisico", contar("select coalesce(sum(stock_fisico), 0) from variante"));
        h.put("reservado", contar("select coalesce(sum(stock_reservado), 0) from variante"));
        BigDecimal ingreso = jdbc.queryForObject("select coalesce(sum(total), 0) from venta", BigDecimal.class);
        h.put("ingreso", ingreso == null ? "0" : ingreso.toPlainString());
        h.put("porTipo", jdbc.queryForList(
                "select tipo, sum(cantidad) as u from movimiento group by tipo order by tipo").stream()
                .map(f -> f.get("tipo") + "=" + f.get("u")).toList());
        return h;
    }

    @Test
    void generar_baseYaCargadaConAlertasPerdidas_lasRecuperaDeFormaIdempotente() {
        generador.generar();
        long esperadas = contar("select count(*) from alerta where estado = 'ACTIVA'");
        assertThat(esperadas).isPositive();
        // simula el fallo posterior al commit: las alertas no llegaron a evaluarse
        jdbc.update("delete from alerta");

        ResumenSemilla segunda = generador.generar();

        assertThat(segunda.generado()).isFalse();
        assertThat(contar("select count(*) from alerta where estado = 'ACTIVA'")).isEqualTo(esperadas);
        generador.generar();
        assertThat(contar("select count(*) from alerta where estado = 'ACTIVA'")).isEqualTo(esperadas);
    }

    @Test
    void verificarCarga_usuarioDemoConOtroRol_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("update usuario set rol = 'INVENTARIO' where email = 'ventas@livorno.co'");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("usuarios demo");
    }
}
