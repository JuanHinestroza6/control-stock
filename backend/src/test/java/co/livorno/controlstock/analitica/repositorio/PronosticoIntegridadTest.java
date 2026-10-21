package co.livorno.controlstock.analitica.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.analitica.modelo.AnaliticaProducto;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.OrigenEjecucion;
import co.livorno.controlstock.analitica.modelo.Pronostico;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
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
import java.util.List;
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

/** V15: claves foráneas, índices y que escribir el pronóstico no toca la variante (ADR-009/ADR-010). */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class PronosticoIntegridadTest {

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 15, 2, 0);

    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired PronosticoRepository pronosticos;
    @Autowired AnaliticaProductoRepository analiticaProductos;
    @Autowired EjecucionAnaliticaRepository ejecuciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    Producto producto;
    Variante variante;
    Long ejecucionId;

    @BeforeEach
    void preparar() {
        Categoria cat = categorias.findAllByOrderByNombreAsc().get(0);
        producto = productos.saveAndFlush(new Producto("LIV903", "Jogger prueba", cat, new BigDecimal("20000"),
                new BigDecimal("59900"), null, null));
        variante = variantes.saveAndFlush(new Variante(producto, "LIV903-M-NEG", Talla.M, "NEGRO", 2));
        ejecucionId = ejecuciones.saveAndFlush(
                EjecucionAnalitica.iniciar(OrigenEjecucion.NOCTURNA, null, AHORA, AHORA.toLocalDate())).getId();
    }

    private void insertarPronostico(long productoId, Long varianteId) {
        jdbc.update("""
                insert into pronostico (producto_id, variante_id, periodo, cantidad_proyectada, metodo, calculado_en,
                    ejecucion_id) values (?, ?, '2026-10-01', 1, 'SES', now(6), ?)""",
                productoId, varianteId, ejecucionId);
    }

    @Test
    void pronostico_productoOVarianteInexistentes_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertarPronostico(-1L, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_pronostico_producto");
        assertThatThrownBy(() -> insertarPronostico(producto.getId(), -1L))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_pronostico_variante");
    }

    @Test
    void analiticaProductoYMes_fkInexistentes_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into analitica_producto (producto_id, ejecucion_id, meses_historia, metodo,
                    unidades_observadas_12m, unidades_corregidas_12m, calculado_en)
                values (-1, ?, 24, 'SES', 1, 1, now(6))""", ejecucionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_analitica_producto_producto");
        assertThatThrownBy(() -> jdbc.update("""
                insert into analitica_producto (producto_id, ejecucion_id, meses_historia, metodo,
                    unidades_observadas_12m, unidades_corregidas_12m, calculado_en)
                values (?, -1, 24, 'SES', 1, 1, now(6))""", producto.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_analitica_producto_ejecucion");
        assertThatThrownBy(() -> jdbc.update("""
                insert into analitica_producto_mes (producto_id, periodo, ejecucion_id, unidades_observadas,
                    disponibilidad, unidades_corregidas) values (-1, '2026-09-01', ?, 1, 1, 1)""", ejecucionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_analitica_producto_mes_producto");
        assertThatThrownBy(() -> jdbc.update("""
                insert into analitica_producto_mes (producto_id, periodo, ejecucion_id, unidades_observadas,
                    disponibilidad, unidades_corregidas) values (?, '2026-09-01', -1, 1, 1, 1)""", producto.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_analitica_producto_mes_ejecucion");
    }

    @Test
    void producto_conPronostico_noSePuedeBorrar() {
        insertarPronostico(producto.getId(), variante.getId());

        assertThatThrownBy(() -> jdbc.update("delete from variante where id = ?", variante.getId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_pronostico_variante");
        assertThatThrownBy(() -> jdbc.update("delete from ejecucion_analitica where id = ?", ejecucionId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_pronostico_ejecucion");
    }

    @Test
    void esquema_indicesDeV15_existen() {
        assertThat(indices("pronostico")).contains("idx_pronostico_variante_periodo", "idx_pronostico_ejecucion",
                "uq_pronostico_producto_variante_periodo");
        assertThat(indices("analitica_producto")).contains("idx_analitica_producto_ejecucion");
        assertThat(indices("analitica_producto_mes")).contains("idx_analitica_producto_mes_ejecucion");
    }

    @Test
    void guardarPronosticoEInstantanea_noModificaLaVariante() {
        em.flush();
        em.clear();
        Variante antes = variantes.findById(variante.getId()).orElseThrow();
        long versionAntes = antes.getVersion();
        var actualizadoAntes = antes.getActualizadoEn();
        var fisicoAntes = antes.getStockFisico();
        var reorden = antes.getPuntoReorden();
        var clase = antes.getClaseAbc();
        em.clear();

        pronosticos.saveAndFlush(new Pronostico(producto.getId(), variante.getId(), LocalDate.of(2026, 10, 1),
                new BigDecimal("4.00"), MetodoPronostico.SES, null, AHORA, ejecucionId));
        analiticaProductos.saveAndFlush(new AnaliticaProducto(producto.getId(), ejecucionId, 30,
                MetodoPronostico.SES, null, null, null, null, null, null, null, 10, new BigDecimal("12.00"), AHORA));
        em.clear();

        Variante despues = variantes.findById(variante.getId()).orElseThrow();
        assertThat(despues.getVersion()).isEqualTo(versionAntes);
        assertThat(despues.getActualizadoEn()).isEqualTo(actualizadoAntes);
        assertThat(despues.getStockFisico()).isEqualTo(fisicoAntes);
        assertThat(despues.getPuntoReorden()).isEqualTo(reorden);
        assertThat(despues.getClaseAbc()).isEqualTo(clase);
    }

    @Test
    void pronostico_cantidadNegativa_lanzaExcepcion() {
        assertThatThrownBy(() -> new Pronostico(1L, null, LocalDate.of(2026, 10, 1), new BigDecimal("-1"),
                MetodoPronostico.SES, null, AHORA, 1L)).isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> indices(String tabla) {
        return jdbc.queryForList("""
                select distinct index_name from information_schema.statistics
                where table_schema = database() and table_name = ?""", String.class, tabla)
                .stream().map(String::toLowerCase).toList();
    }
}
