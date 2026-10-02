package co.livorno.controlstock.catalogo.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class VarianteRepositoryTest {

    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired JdbcTemplate jdbc;

    Producto producto;

    @BeforeEach
    void preparar() {
        Categoria cat = categorias.findAllByOrderByNombreAsc().get(0);
        producto = crearProducto("LIV001", cat);
    }

    private Producto crearProducto(String codigo, Categoria cat) {
        return productos.saveAndFlush(new Producto(codigo, "Camiseta " + codigo, cat, new BigDecimal("20000"),
                new BigDecimal("59900"), null, null));
    }

    private Variante crear(Producto p, String sku, Talla talla, String color) {
        return variantes.saveAndFlush(new Variante(p, sku, talla, color, 2));
    }

    private void insertNativo(String sku, String talla, int fisico, int reservado, String abc) {
        jdbc.update("""
                insert into variante (producto_id, sku, talla, color, stock_fisico, stock_reservado, clase_abc,
                                      creado_en, actualizado_en)
                values (?, ?, ?, 'X', ?, ?, ?, now(6), now(6))""", producto.getId(), sku, talla, fisico, reservado, abc);
    }

    @Test
    void guardar_persisteConStockEnCeroVersionYAuditoria() {
        Variante v = crear(producto, "LIV001-M-NEG", Talla.M, "negro");

        Variante leida = variantes.findById(v.getId()).orElseThrow();
        assertThat(leida.getStockFisico()).isZero();
        assertThat(leida.getStockReservado()).isZero();
        assertThat(leida.getVersion()).isZero();
        assertThat(leida.getTalla()).isEqualTo(Talla.M);
        assertThat(leida.getColor()).isEqualTo("NEGRO");
        assertThat(leida.getClaseAbc()).isNull();
        assertThat(leida.getCreadoEn()).isNotNull();
    }

    @Test
    void guardar_skuDuplicado_violaUnicidad() {
        crear(producto, "LIV001-M-NEG", Talla.M, "NEGRO");

        assertThatThrownBy(() -> crear(producto, "LIV001-M-NEG", Talla.L, "NEGRO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardar_mismaTallaYColorEnElProducto_violaUnicidad() {
        crear(producto, "A", Talla.M, "NEGRO");

        assertThatThrownBy(() -> crear(producto, "B", Talla.M, "NEGRO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_reservadoMayorQueFisico_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertNativo("S1", "M", 1, 2, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_variante_reservado_lte_fisico");
    }

    @Test
    void check_stockFisicoNegativo_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertNativo("S2", "M", -1, 0, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Check constraint");
    }

    @Test
    void check_tallaInvalidaYClaseAbcInvalida_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> insertNativo("S3", "XXXL", 0, 0, null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_variante_talla");
        assertThatThrownBy(() -> insertNativo("S4", "M", 0, 0, "Z"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_variante_clase_abc");
    }

    @Test
    void totalesPorProducto_sumaSoloVariantesActivasEnUnaConsulta() {
        Producto otro = crearProducto("LIV002", producto.getCategoria());
        crear(producto, "A", Talla.S, "NEGRO");
        crear(producto, "B", Talla.M, "NEGRO");
        crear(producto, "C", Talla.L, "NEGRO").desactivar();
        crear(otro, "D", Talla.S, "NEGRO");
        jdbc.update("update variante set stock_fisico = 10, stock_reservado = 3 where sku = 'A'");
        jdbc.update("update variante set stock_fisico = 5 where sku = 'B'");
        jdbc.update("update variante set stock_fisico = 99 where sku = 'C'");
        variantes.flush();

        List<TotalesProducto> t = variantes.totalesPorProducto(List.of(producto.getId(), otro.getId()));

        assertThat(t).containsExactlyInAnyOrder(new TotalesProducto(producto.getId(), 2L, 12L),
                new TotalesProducto(otro.getId(), 1L, 0L));
        assertThat(variantes.countByProductoIdAndActivoTrue(producto.getId())).isEqualTo(2);
    }
}
