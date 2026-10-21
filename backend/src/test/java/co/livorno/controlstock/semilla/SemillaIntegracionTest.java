package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.auth.dto.LoginRequest;
import co.livorno.controlstock.auth.dto.LoginResponse;
import co.livorno.controlstock.auth.servicio.AuthService;
import co.livorno.controlstock.analitica.servicio.EjecucionAnaliticaService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static final LocalDate FIN = LocalDate.of(2025, 12, 31);
    private static final String[] TABLAS = {"usuario", "categoria", "producto", "variante", "movimiento", "venta",
            "detalle_venta", "devolucion", "reserva", "alerta", "ejecucion_analitica", "analitica_variante", "plan_compra", "detalle_plan"};

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
    @MockitoSpyBean
    EjecucionAnaliticaService ejecucionAnalitica;

    @BeforeEach
    void prepararBaseYReloj() {
        LimpiezaSemilla.limpiar(jdbc);
        LimpiezaSemilla.fijarCierreTrasFin(reloj, FIN);
    }

    /** Un solo @AfterEach: el reloj real se restablece siempre, aunque la limpieza falle. */
    @AfterEach
    void limpiarYRestablecerReloj() {
        try {
            LimpiezaSemilla.limpiar(jdbc);
        } finally {
            LimpiezaSemilla.restablecerReloj(reloj);
        }
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
    void generar_cierre_clasificaTodasLasVariantesYCalculaElRopConHistoria() {
        ResumenSemilla r = generador.generar();

        assertThat(contar("""
                select count(*) from variante v join producto p on p.id = v.producto_id
                where v.activo = 1 and p.activo = 1 and v.clase_abc is null""")).isZero();
        assertThat(contar("""
                select count(*) from analitica_variante a join variante v on v.id = a.variante_id
                where a.dias_historia >= 30 and v.punto_reorden is null""")).isZero();
        assertThat(contar("select count(*) from analitica_variante where dias_historia >= 30")).isPositive();
        // el cierre con corte = fecha-fin + 1 ve ventas recientes: hay demanda y ROP calculado por distribución
        assertThat(contar("select count(*) from analitica_variante where metodo_reorden in ('NORMAL', 'POISSON')"))
                .isPositive();
        verificador.verificarAnalitica();
        var cierre = r.analitica();
        assertThat(cierre.claseA() + cierre.claseB() + cierre.claseC()).isEqualTo(cierre.variantesActivas());
        assertThat(cierre.ropPorMetodo().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(cierre.variantesActivas());
        assertThat(r.texto()).contains("analítica de cierre");
    }

    @Test
    void generar_cierre_guardaUnaEjecucionSemillaExitosaCuyaMetricaCoincideConElConteoSql() {
        ResumenSemilla r = generador.generar();

        assertThat(contar("select count(*) from ejecucion_analitica")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select origen from ejecucion_analitica", String.class)).isEqualTo("SEMILLA");
        assertThat(jdbc.queryForObject("select estado from ejecucion_analitica", String.class)).isEqualTo("EXITOSA");
        assertThat(jdbc.queryForObject("select fecha_corte from ejecucion_analitica", LocalDate.class))
                .isEqualTo(FIN.plusDays(1));
        long enAlerta = contar("""
                select count(distinct v.id) from alerta a join variante v on v.id = a.variante_id
                join producto p on p.id = v.producto_id
                where a.estado = 'ACTIVA' and a.tipo in ('AGOTADO', 'STOCK_BAJO') and v.activo = 1 and p.activo = 1""");
        long activas = contar("""
                select count(*) from variante v join producto p on p.id = v.producto_id
                where v.activo = 1 and p.activo = 1""");
        BigDecimal esperado = BigDecimal.valueOf(enAlerta * 100).divide(BigDecimal.valueOf(activas), 1,
                java.math.RoundingMode.HALF_UP);
        assertThat(r.analitica().enAlertaStock()).isEqualTo((int) enAlerta);
        assertThat(r.analitica().variantesActivas()).isEqualTo((int) activas);
        assertThat(r.analitica().porcentajeAlerta()).isEqualByComparingTo(esperado);
        assertThat(r.alertasAgotado() + r.alertasStockBajo()).isEqualTo(enAlerta);
    }

    @Test
    void generar_segundaEjecucion_noCreaOtraEjecucionAnaliticaNiTocaAlertas() {
        generador.generar();
        Map<String, Long> antes = conteos();
        long revisadas = contar("select count(*) from alerta where revisada_en is not null");

        ResumenSemilla segunda = generador.generar();

        assertThat(segunda.generado()).isFalse();
        assertThat(conteos()).isEqualTo(antes);
        assertThat(contar("select count(*) from ejecucion_analitica")).isEqualTo(1);
        assertThat(contar("select count(*) from alerta where revisada_en is not null")).isEqualTo(revisadas);
    }

    @Test
    void generar_analiticaDeCierreFallida_avisaCómoRecuperarseYLaCargaQuedaConfirmada() {
        doThrow(new IllegalStateException("fallo forzado de la analítica")).when(ejecucionAnalitica)
                .ejecutar(any(), any());

        assertThatThrownBy(generador::generar).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("Recalcular");

        assertThat(contar("select count(*) from venta")).isPositive();
        assertThat(reloj.estaSimulando()).isFalse();
        // una nueva ejecución dice "ya hay datos" y no hace nada
        assertThat(generador.generar().generado()).isFalse();
    }

    @Test
    void verificarCarga_usuarioDemoConOtroRol_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("update usuario set rol = 'INVENTARIO' where email = 'ventas@livorno.co'");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("usuarios demo");
    }

    private Map<Long, Integer> enCaminoReal() {
        Map<Long, Integer> m = new java.util.HashMap<>();
        jdbc.query("""
                select d.variante_id, sum(d.cantidad_aprobada) from detalle_plan d
                join plan_compra p on p.id = d.plan_id where p.estado = 'APROBADO' group by d.variante_id""",
                rs -> { m.put(rs.getLong(1), rs.getInt(2)); });
        return m;
    }

    @Test
    void generar_alCerrar_hayPlanesAprobadosEnCaminoYNingunoBorradorNiCancelado() {
        generador.generar();

        assertThat(contar("select count(*) from plan_compra where estado = 'APROBADO'")).isPositive();
        assertThat(contar("select count(*) from plan_compra where estado = 'RECIBIDO'")).isPositive();
        assertThat(contar("select count(*) from plan_compra where estado in ('BORRADOR', 'CANCELADO')")).isZero();
        assertThat(enCaminoReal()).isNotEmpty();
    }

    @Test
    void generar_planes_losUsuariosSonLosDemoDeCadaRol() {
        generador.generar();

        assertThat(contar("""
                select count(*) from plan_compra p
                join usuario c on c.id = p.creado_por
                left join usuario a on a.id = p.aprobado_por
                left join usuario r on r.id = p.recibido_por
                where c.email <> 'inventario@livorno.co' or a.email <> 'admin@livorno.co'
                or (p.estado = 'RECIBIDO' and (r.email is null or r.email <> 'inventario@livorno.co'))
                or (p.estado = 'APROBADO' and p.recibido_por is not null)""")).isZero();
        assertThat(contar("""
                select count(*) from movimiento m join usuario u on u.id = m.usuario_id
                where m.tipo = 'ENTRADA' and m.referencia_tipo = 'PLAN_COMPRA' and u.email <> 'inventario@livorno.co'"""))
                .isZero();
    }

    @Test
    void verificarEnCamino_conElArregloEsperadoCorrecto_noFalla() {
        generador.generar();

        verificador.verificarEnCamino(enCaminoReal());
    }

    @Test
    void verificarEnCamino_conUnidadesDistintasEnUnaVariante_falla() {
        generador.generar();
        Map<Long, Integer> esperado = enCaminoReal();
        Long id = esperado.keySet().iterator().next();
        esperado.put(id, esperado.get(id) + 1);

        assertThatThrownBy(() -> verificador.verificarEnCamino(esperado))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("en camino de 1 variantes");
    }

    @Test
    void verificarEnCamino_conUnaVarianteEsperadaQueNoTienePedido_falla() {
        generador.generar();
        Map<Long, Integer> esperado = enCaminoReal();
        Long otra = jdbc.queryForList("select id from variante order by id", Long.class).stream()
                .filter(v -> !esperado.containsKey(v)).findFirst().orElseThrow();
        esperado.put(otra, 5);

        assertThatThrownBy(() -> verificador.verificarEnCamino(esperado))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("no coincide");
    }

    @Test
    void verificarEnCamino_conUnPedidoRealQueLaSimulacionNoEsperaba_falla() {
        generador.generar();
        Map<Long, Integer> esperado = enCaminoReal();
        esperado.remove(esperado.keySet().iterator().next());

        assertThatThrownBy(() -> verificador.verificarEnCamino(esperado))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("no coincide");
    }

    @Test
    void generar_verificacionDeEnCaminoFallida_revierteTodaLaCarga() {
        Map<String, Long> antes = conteos();
        doThrow(new SemillaAbortadaException("en camino forzado")).when(verificador).verificarEnCamino(any());

        assertThatThrownBy(generador::generar).isInstanceOf(SemillaAbortadaException.class)
                .hasMessageContaining("en camino forzado");

        assertThat(conteos()).isEqualTo(antes);
        assertThat(reloj.estaSimulando()).isFalse();
    }

    @Test
    void verificarCarga_planEnBorrador_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("update plan_compra set estado = 'BORRADOR' where id = (select id from (select min(id) id from plan_compra where estado = 'APROBADO') t)");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("plan de compras fuera de");
    }

    @Test
    void verificarCarga_planRecibidoSinSuEntrada_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("""
                update movimiento set referencia_tipo = 'VENTA' where id = (select id from (select min(id) id from movimiento
                where tipo = 'ENTRADA' and referencia_tipo = 'PLAN_COMPRA') t)""");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("plan RECIBIDO sin una ENTRADA");
    }

    @Test
    void verificarCarga_entradaSueltaFueraDeUnPlan_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("""
                update movimiento set referencia_tipo = null, referencia_id = null, observacion = 'manual'
                where id = (select id from (select min(id) id from movimiento
                where tipo = 'ENTRADA' and referencia_tipo = 'PLAN_COMPRA') t)""");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("ENTRADA que no viene de un plan");
    }

    @Test
    void verificarCarga_planConFechaFueraDelRango_falla() {
        ResumenSemilla r = generador.generar();
        jdbc.update("update plan_compra set creado_en = '2030-01-01 10:00:00' where id = (select id from (select min(id) id from plan_compra) t)");

        assertThatThrownBy(() -> verificador.verificarCarga(r.inicio(), r.fin()))
                .isInstanceOf(SemillaAbortadaException.class).hasMessageContaining("plan_compra con fechas fuera");
    }
}
