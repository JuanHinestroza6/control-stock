package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.ResultadoEjecucion;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
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

/** Esquema V1-V15 con ddl-auto=validate, CHECK de las tablas de analítica y escritura dirigida de ROP/clase (ADR-009). */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class AnaliticaEsquemaTest {

    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired AnaliticaVarianteRepository analiticaVariantes;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Variante variante;
    Long ejecucionId;

    @BeforeEach
    void preparar() {
        Categoria cat = categorias.findAllByOrderByNombreAsc().get(0);
        Producto p = productos.saveAndFlush(new Producto("LIV901", "Camiseta prueba", cat, new BigDecimal("20000"),
                new BigDecimal("59900"), null, null));
        variante = variantes.saveAndFlush(new Variante(p, "LIV901-M-NEG", Talla.M, "NEGRO", 2));
        ejecucionId = insertarEjecucion("NOCTURNA", null, "EXITOSA", "now(6)");
    }

    // ---------- CHECK de ejecucion_analitica ----------

    private Long insertarEjecucion(String origen, Long usuarioId, String estado, String terminadaEn) {
        jdbc.update("insert into ejecucion_analitica (origen, usuario_id, iniciada_en, terminada_en, estado, fecha_corte)"
                + " values (?, ?, now(6), " + terminadaEn + ", ?, curdate())", origen, usuarioId, estado);
        return jdbc.queryForObject("select max(id) from ejecucion_analitica", Long.class);
    }

    @Test
    void ejecucion_origenYEstadoValidos_seInserta() {
        Long usuario = jdbc.queryForObject("select min(id) from usuario", Long.class);

        insertarEjecucion("MANUAL", usuario, "FALLIDA", "now(6)");
        insertarEjecucion("SEMILLA", null, "EN_CURSO", "null");

        assertThat(jdbc.queryForObject("select count(*) from ejecucion_analitica", Long.class)).isEqualTo(3);
    }

    @Test
    void ejecucion_origenInvalido_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarEjecucion("OTRO", null, "EXITOSA", "now(6)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_origen");
    }

    @Test
    void ejecucion_estadoInvalido_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarEjecucion("NOCTURNA", null, "PAUSADA", "now(6)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_estado");
    }

    @Test
    void ejecucion_enCursoConFechaDeFinOTerminadaSinFecha_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarEjecucion("NOCTURNA", null, "EN_CURSO", "now(6)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_terminada");
        assertThatThrownBy(() -> insertarEjecucion("NOCTURNA", null, "EXITOSA", "null"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_terminada");
    }

    @Test
    void ejecucion_manualSinUsuarioOUsuarioEnNoManual_loRechazaLaBaseDeDatos() {
        Long usuario = jdbc.queryForObject("select min(id) from usuario", Long.class);

        assertThatThrownBy(() -> insertarEjecucion("MANUAL", null, "EXITOSA", "now(6)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_usuario");
        assertThatThrownBy(() -> insertarEjecucion("NOCTURNA", usuario, "EXITOSA", "now(6)"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ejecucion_analitica_usuario");
    }

    // ---------- CHECK de analitica_variante ----------

    private void insertarAnalitica(Long varianteId, String clase, String metodo, Integer rop, String ingreso,
            int unidades) {
        jdbc.update("""
                insert into analitica_variante (variante_id, ejecucion_id, unidades_netas, ingreso_neto, participacion,
                    acumulado, posicion, clase_abc, dias_historia, demanda_media, desviacion, lead_time_dias,
                    nivel_servicio, z, stock_seguridad, punto_reorden, metodo_reorden, calculado_en)
                values (?, ?, ?, ?, 0.5, 0.5, 1, ?, 90, 1.5, 0.5, 15, 0.95, 1.65, 3.2, ?, ?, now(6))""",
                varianteId, ejecucionId, unidades, new BigDecimal(ingreso), clase, rop, metodo);
    }

    @Test
    void analitica_filaValida_seInserta() {
        insertarAnalitica(variante.getId(), "A", "NORMAL", 10, "100.00", 3);

        assertThat(jdbc.queryForObject("select count(*) from analitica_variante", Long.class)).isEqualTo(1);
    }

    @Test
    void analitica_claseOMetodoInvalidos_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "D", "NORMAL", 10, "100.00", 3))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_clase");
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "A", "MAGICO", 10, "100.00", 3))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_metodo");
    }

    @Test
    void analitica_sinHistoriaSoloConRopNulo_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "C", "SIN_HISTORIA", 3, "0.00", 0))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_sin_historia");
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "C", "POISSON", null, "0.00", 0))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_sin_historia");

        insertarAnalitica(variante.getId(), "C", "SIN_HISTORIA", null, "0.00", 0);
        assertThat(jdbc.queryForObject("select count(*) from analitica_variante", Long.class)).isEqualTo(1);
    }

    @Test
    void analitica_ingresoOUnidadesNegativos_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "C", "NORMAL", 1, "-0.01", 0))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_ingreso");
        assertThatThrownBy(() -> insertarAnalitica(variante.getId(), "C", "NORMAL", 1, "1.00", -1))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_analitica_variante_unidades");
    }

    // ---------- Entidades ----------

    @Test
    void ejecucionAnalitica_cicloIniciarYTerminar_persisteConteosYMetrica() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 14, 2, 0, 0);
        EjecucionAnalitica e = ejecuciones.saveAndFlush(
                EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, inicio, LocalDate.of(2026, 10, 14)));
        e.terminar(inicio.plusSeconds(3), new ResultadoEjecucion(299, 60, 90, 149, 250, 299, 0, 299, 90, 4));
        ejecuciones.saveAndFlush(e);
        em.clear();

        EjecucionAnalitica leida = ejecuciones.findById(e.getId()).orElseThrow();
        assertThat(leida.getEstado()).isEqualTo(EstadoEjecucion.EXITOSA);
        assertThat(leida.getDuracionMs()).isEqualTo(3000);
        assertThat(leida.getClaseA()).isEqualTo(60);
        assertThat(leida.getPorcentajeAlertaStock()).isEqualByComparingTo("30.1");
        assertThat(ejecuciones.findFirstByOrderByIniciadaEnDescIdDesc()).isPresent();
    }

    @Test
    void ejecucionAnalitica_manualSinUsuarioOTerminarDosVeces_lanzaExcepcion() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 14, 2, 0);
        assertThatThrownBy(() -> EjecucionAnalitica.iniciar(OrigenEjecucion.MANUAL, null, ahora, ahora.toLocalDate()))
                .isInstanceOf(IllegalArgumentException.class);

        EjecucionAnalitica e = EjecucionAnalitica.iniciar(OrigenEjecucion.SEMILLA, null, ahora, ahora.toLocalDate());
        e.fallar(ahora.plusSeconds(1), "x".repeat(400));
        assertThat(e.getMensaje()).hasSize(255);
        assertThatThrownBy(() -> e.fallar(ahora, "otra vez")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void analiticaVariante_guardarYLeer_persisteLaInstantanea() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 14, 2, 0);
        analiticaVariantes.saveAndFlush(new AnaliticaVariante(variante.getId(), ejecucionId, 12,
                new BigDecimal("345.50"), new BigDecimal("0.123456"), new BigDecimal("0.800000"), 4, 'A', 90,
                new BigDecimal("0.1333"), new BigDecimal("0.3500"), 15, new BigDecimal("0.950"),
                new BigDecimal("1.65"), new BigDecimal("2.10"), 4, MetodoReorden.POISSON, ahora));
        em.clear();

        AnaliticaVariante leida = analiticaVariantes.findById(variante.getId()).orElseThrow();
        assertThat(leida.getClaseAbc()).isEqualTo('A');
        assertThat(leida.getMetodoReorden()).isEqualTo(MetodoReorden.POISSON);
        assertThat(leida.getPuntoReorden()).isEqualTo(4);
        assertThat(leida.getIngresoNeto()).isEqualByComparingTo("345.50");
        assertThat(leida.isNew()).isFalse();
    }

    // ---------- Escritura dirigida de ROP y clase ----------

    @Test
    void aplicarAVariante_fijaRopYClaseSinTocarVersionNiStock() {
        long versionAntes = jdbc.queryForObject("select version from variante where id = ?", Long.class,
                variante.getId());

        int filas = analiticaVariantes.aplicarAVariante(variante.getId(), 7, "A");

        assertThat(filas).isEqualTo(1);
        assertThat(jdbc.queryForObject("select punto_reorden from variante where id = ?", Integer.class,
                variante.getId())).isEqualTo(7);
        assertThat(jdbc.queryForObject("select clase_abc from variante where id = ?", String.class,
                variante.getId())).isEqualTo("A");
        assertThat(jdbc.queryForObject("select version from variante where id = ?", Long.class, variante.getId()))
                .isEqualTo(versionAntes);
    }

    @Test
    void guardarVarianteCargada_noPisaRopNiClaseFijadosPorSql() {
        // dado: la entidad se cargó antes del cálculo (puntoReorden/claseAbc null en memoria)
        Variante cargada = variantes.findById(variante.getId()).orElseThrow();
        assertThat(cargada.getPuntoReorden()).isNull();
        analiticaVariantes.aplicarAVariante(variante.getId(), 7, "B");

        // cuando: se edita otro campo y se guarda la entidad vieja
        cargada.actualizarStockMinimo(5);
        variantes.saveAndFlush(cargada);

        // entonces: el UPDATE de la entidad no reescribió ROP ni clase, pero sí el campo editado
        assertThat(jdbc.queryForObject("select punto_reorden from variante where id = ?", Integer.class,
                variante.getId())).isEqualTo(7);
        assertThat(jdbc.queryForObject("select clase_abc from variante where id = ?", String.class,
                variante.getId())).isEqualTo("B");
        assertThat(jdbc.queryForObject("select stock_minimo from variante where id = ?", Integer.class,
                variante.getId())).isEqualTo(5);
    }

    @Test
    void bloquearSiLibre_varianteSinBloqueos_devuelveSuIdYInexistenteDevuelveVacio() {
        assertThat(analiticaVariantes.bloquearSiLibre(variante.getId())).containsExactly(variante.getId());
        assertThat(analiticaVariantes.bloquearSiLibre(-1L)).isEmpty();
    }

    @Test
    void variante_puntoReordenYClaseAbc_estanMapeadosComoNoActualizables() throws NoSuchFieldException {
        for (String nombre : new String[] {"puntoReorden", "claseAbc"}) {
            Field campo = Variante.class.getDeclaredField(nombre);
            assertThat(campo.getAnnotation(jakarta.persistence.Column.class).updatable())
                    .as("%s debe ser updatable = false", nombre).isFalse();
        }
    }
}
