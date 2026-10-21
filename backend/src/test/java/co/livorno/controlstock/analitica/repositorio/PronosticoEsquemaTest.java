package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.config.PropiedadesPronostico;
import co.livorno.controlstock.analitica.modelo.AnaliticaProducto;
import co.livorno.controlstock.analitica.modelo.AnaliticaProductoMes;
import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.Pronostico;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Esquema V15 con ddl-auto=validate: tablas de pronóstico, CHECK, unicidad con variante nula y columnas nuevas. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class PronosticoEsquemaTest {

    static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 15, 2, 0);

    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PronosticoRepository pronosticos;
    @Autowired AnaliticaProductoRepository analiticaProductos;
    @Autowired AnaliticaProductoMesRepository analiticaMeses;
    @Autowired AnaliticaVarianteRepository analiticaVariantes;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Producto producto;
    Variante variante;
    Long ejecucionId;

    @BeforeEach
    void preparar() {
        Categoria cat = categorias.findAllByOrderByNombreAsc().get(0);
        producto = productos.saveAndFlush(new Producto("LIV902", "Hoodie prueba", cat, new BigDecimal("20000"),
                new BigDecimal("59900"), null, null));
        variante = variantes.saveAndFlush(new Variante(producto, "LIV902-M-NEG", Talla.M, "NEGRO", 2));
        ejecucionId = ejecuciones.saveAndFlush(
                EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, AHORA, AHORA.toLocalDate())).getId();
    }

    private void insertarPronostico(Long varianteId, String periodo, String cantidad, String metodo) {
        jdbc.update("""
                insert into pronostico (producto_id, variante_id, periodo, cantidad_proyectada, metodo, calculado_en,
                    ejecucion_id) values (?, ?, ?, ?, ?, now(6), ?)""",
                producto.getId(), varianteId, periodo, new BigDecimal(cantidad), metodo, ejecucionId);
    }

    private void insertarAnaliticaProducto(String metodo, String beta, String motivo) {
        jdbc.update("""
                insert into analitica_producto (producto_id, ejecucion_id, meses_historia, metodo, beta, motivo_sin_hw,
                    unidades_observadas_12m, unidades_corregidas_12m, calculado_en)
                values (?, ?, 24, ?, ?, ?, 100, 120.50, now(6))""",
                producto.getId(), ejecucionId, metodo, beta == null ? null : new BigDecimal(beta), motivo);
    }

    // ---------- pronostico: CHECK y unicidad ----------

    @Test
    void pronostico_filaValida_seInserta() {
        insertarPronostico(null, "2026-10-01", "31.50", "SES");
        insertarPronostico(variante.getId(), "2026-10-01", "10.00", "HOLT_WINTERS");

        assertThat(jdbc.queryForObject("select count(*) from pronostico", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select variante_clave from pronostico where variante_id is null", Long.class))
                .isZero();
    }

    @Test
    void pronostico_metodoCantidadOPeriodoInvalidos_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarPronostico(null, "2026-10-01", "1.00", "SIN_HISTORIA"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_pronostico_metodo");
        assertThatThrownBy(() -> insertarPronostico(null, "2026-10-01", "-0.01", "SES"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_pronostico_cantidad");
        assertThatThrownBy(() -> insertarPronostico(null, "2026-10-15", "1.00", "SES"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_pronostico_periodo");
    }

    @Test
    void pronostico_duplicadoDelProductoConVarianteNula_loRechazaLaBaseDeDatos() {
        insertarPronostico(null, "2026-10-01", "5.00", "SES");

        assertThatThrownBy(() -> insertarPronostico(null, "2026-10-01", "6.00", "SES"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_pronostico_producto_variante_periodo");
        // otro periodo y la misma variante en otro periodo sí caben
        insertarPronostico(null, "2026-11-01", "6.00", "SES");
        insertarPronostico(variante.getId(), "2026-10-01", "2.00", "SES");
        assertThatThrownBy(() -> insertarPronostico(variante.getId(), "2026-10-01", "3.00", "SES"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uq_pronostico_producto_variante_periodo");
    }

    @Test
    void pronostico_ejecucionOProductoInexistentes_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into pronostico (producto_id, periodo, cantidad_proyectada, metodo, calculado_en, ejecucion_id)
                values (?, '2026-10-01', 1, 'SES', now(6), -1)""", producto.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_pronostico_ejecucion");
    }

    // ---------- analitica_producto: CHECK ----------

    @Test
    void analiticaProducto_combinacionesValidas_seInsertan() {
        insertarAnaliticaProducto("SES", null, "POCA_HISTORIA");
        assertThat(jdbc.queryForObject("select count(*) from analitica_producto", Long.class)).isEqualTo(1);
    }

    @Test
    void analiticaProducto_holtWintersRequiereBetaYNoMotivo_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarAnaliticaProducto("HOLT_WINTERS", null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_producto_beta");
        assertThatThrownBy(() -> insertarAnaliticaProducto("SES", "0.10", null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_producto_beta");
        assertThatThrownBy(() -> insertarAnaliticaProducto("HOLT_WINTERS", "0.10", "POCA_HISTORIA"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_producto_motivo_hw");

        insertarAnaliticaProducto("HOLT_WINTERS", "0.10", null);
        assertThat(jdbc.queryForObject("select count(*) from analitica_producto", Long.class)).isEqualTo(1);
    }

    @Test
    void analiticaProducto_metodoOMotivoInvalidos_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarAnaliticaProducto("ARIMA", null, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_producto_metodo");
        assertThatThrownBy(() -> insertarAnaliticaProducto("SES", null, "OTRO"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_producto_motivo");
    }

    // ---------- analitica_producto_mes: CHECK ----------

    @Test
    void analiticaProductoMes_disponibilidadFueraDeRango_loRechazaLaBaseDeDatos() {
        String sql = """
                insert into analitica_producto_mes (producto_id, periodo, ejecucion_id, unidades_observadas,
                    disponibilidad, unidades_corregidas) values (?, '2026-09-01', ?, 10, ?, 12)""";
        jdbc.update(sql, producto.getId(), ejecucionId, new BigDecimal("1.0000"));

        assertThatThrownBy(() -> jdbc.update(sql.replace("2026-09-01", "2026-08-01"), producto.getId(), ejecucionId,
                new BigDecimal("1.0001"))).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_analitica_producto_mes_disponibilidad");
    }

    // ---------- Entidades con validate ----------

    @Test
    void pronostico_guardarYLeer_persisteTodosLosCampos() {
        pronosticos.saveAndFlush(new Pronostico(producto.getId(), null, OCT, new BigDecimal("31.25"),
                MetodoPronostico.SES, new BigDecimal("0.1850"), AHORA, ejecucionId));
        pronosticos.saveAndFlush(new Pronostico(producto.getId(), variante.getId(), NOV, new BigDecimal("12.00"),
                MetodoPronostico.HOLT_WINTERS, null, AHORA, ejecucionId));
        em.clear();

        var todos = pronosticos.findAll();
        assertThat(todos).hasSize(2);
        Pronostico total = todos.stream().filter(p -> p.getVarianteId() == null).findFirst().orElseThrow();
        assertThat(total.getCantidadProyectada()).isEqualByComparingTo("31.25");
        assertThat(total.getErrorWape()).isEqualByComparingTo("0.1850");
        assertThat(total.getPeriodo()).isEqualTo(OCT);
        assertThat(total.getMetodo()).isEqualTo(MetodoPronostico.SES);
    }

    @Test
    void pronostico_periodoQueNoEsDiaUnoOSinHistoria_lanzaExcepcion() {
        assertThatThrownBy(() -> new Pronostico(1L, null, LocalDate.of(2026, 10, 2), BigDecimal.ONE,
                MetodoPronostico.SES, null, AHORA, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pronostico(1L, null, OCT, BigDecimal.ONE, MetodoPronostico.SIN_HISTORIA, null,
                AHORA, 1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void analiticaProducto_guardarYLeer_persisteLaInstantaneaYLaSerieMensual() {
        analiticaProductos.saveAndFlush(new AnaliticaProducto(producto.getId(), ejecucionId, 30,
                MetodoPronostico.SES, null, null, null, new BigDecimal("0.2000"), null, new BigDecimal("0.2000"),
                MotivoSinHoltWinters.MES_EN_CERO, 100, new BigDecimal("120.50"), AHORA));
        analiticaMeses.saveAndFlush(new AnaliticaProductoMes(producto.getId(), LocalDate.of(2026, 9, 1), ejecucionId,
                70, new BigDecimal("0.8750"), new BigDecimal("80.00"), new BigDecimal("75.00"), null));
        analiticaMeses.saveAndFlush(new AnaliticaProductoMes(producto.getId(), LocalDate.of(2026, 8, 1), ejecucionId,
                60, new BigDecimal("1.0000"), new BigDecimal("60.00"), null, null));
        em.clear();

        AnaliticaProducto ap = analiticaProductos.findById(producto.getId()).orElseThrow();
        assertThat(ap.getMotivoSinHw()).isEqualTo(MotivoSinHoltWinters.MES_EN_CERO);
        assertThat(ap.getUnidadesCorregidas12m()).isEqualByComparingTo("120.50");
        assertThat(ap.isNew()).isFalse();
        var meses = analiticaMeses.findByIdProductoIdOrderByIdPeriodoAsc(producto.getId());
        assertThat(meses).extracting(AnaliticaProductoMes::getPeriodo)
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(meses.get(1).getDisponibilidad()).isEqualByComparingTo("0.8750");
        assertThat(meses.get(1).getBacktestSes()).isEqualByComparingTo("75.00");
    }

    // ---------- Columnas nuevas ----------

    @Test
    void analiticaVariante_fuenteDemanda_porDefectoHistoricaYPronosticoGuardaInsumos() {
        AnaliticaVariante historica = nuevaAnalitica();
        analiticaVariantes.saveAndFlush(historica);
        em.clear();
        AnaliticaVariante leida = analiticaVariantes.findById(variante.getId()).orElseThrow();
        assertThat(leida.getFuenteDemanda()).isEqualTo(FuenteDemanda.HISTORICA);
        assertThat(leida.getDemandaLeadTime()).isNull();

        leida.usarPronostico(new BigDecimal("23.00"), new BigDecimal("1.5000"));
        analiticaVariantes.saveAndFlush(leida);
        em.clear();
        AnaliticaVariante conPronostico = analiticaVariantes.findById(variante.getId()).orElseThrow();
        assertThat(conPronostico.getFuenteDemanda()).isEqualTo(FuenteDemanda.PRONOSTICO);
        assertThat(conPronostico.getDemandaLeadTime()).isEqualByComparingTo("23.00");
        assertThat(conPronostico.getTasaPronosticada()).isEqualByComparingTo("1.5000");
    }

    @Test
    void analiticaVariante_fuenteInvalida_loRechazaLaBaseDeDatos() {
        analiticaVariantes.saveAndFlush(nuevaAnalitica());

        assertThatThrownBy(() -> jdbc.update("update analitica_variante set fuente_demanda = 'OTRA'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_fuente");
    }

    @Test
    void ejecucionAnalitica_columnasDePronosticoYPosicion_sePersisten() {
        EjecucionAnalitica e = ejecuciones.findById(ejecucionId).orElseThrow();
        e.registrarPronostico(30, 4, 26, new BigDecimal("0.2310"));
        e.registrarPosicion(120, 15);
        ejecuciones.saveAndFlush(e);
        em.clear();

        EjecucionAnalitica leida = ejecuciones.findById(ejecucionId).orElseThrow();
        assertThat(leida.getProductosPronosticados()).isEqualTo(30);
        assertThat(leida.getConHoltWinters()).isEqualTo(4);
        assertThat(leida.getConSes()).isEqualTo(26);
        assertThat(leida.getWapeGlobal()).isEqualByComparingTo("0.2310");
        assertThat(leida.getEnAlertaSinTransito()).isEqualTo(120);
        assertThat(leida.getConPedidoEnCamino()).isEqualTo(15);
    }

    // ---------- Propiedades ----------

    @Test
    void propiedadesPronostico_valoresDelPlan_yValidaciones() {
        PropiedadesPronostico p = new PropiedadesPronostico(36, new BigDecimal("0.5"), 180, 3, 6);
        assertThat(p.horizonte()).isEqualTo(3);

        assertThatThrownBy(() -> new PropiedadesPronostico(36, BigDecimal.ZERO, 180, 3, 6))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PropiedadesPronostico(36, new BigDecimal("0.5"), 180, 0, 6))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PropiedadesPronostico(5, new BigDecimal("0.5"), 180, 3, 6))
                .isInstanceOf(IllegalStateException.class);
    }

    private AnaliticaVariante nuevaAnalitica() {
        return new AnaliticaVariante(variante.getId(), ejecucionId, 12, new BigDecimal("345.50"),
                new BigDecimal("0.123456"), new BigDecimal("0.800000"), 4, 'A', 90, new BigDecimal("0.1333"),
                new BigDecimal("0.3500"), 15, new BigDecimal("0.950"), new BigDecimal("1.65"),
                new BigDecimal("2.10"), 4, MetodoReorden.POISSON, AHORA);
    }
}
