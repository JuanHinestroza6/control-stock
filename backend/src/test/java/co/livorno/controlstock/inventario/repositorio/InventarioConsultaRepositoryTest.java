package co.livorno.controlstock.inventario.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class InventarioConsultaRepositoryTest {

    @Autowired InventarioRepository inventario;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired JdbcTemplate jdbc;

    Categoria categoria;
    Producto producto;

    @BeforeEach
    void preparar() {
        categoria = categorias.findAllByOrderByNombreAsc().get(0);
        producto = productos.saveAndFlush(new Producto("LIV001", "Camiseta Basic", categoria,
                new BigDecimal("20000"), new BigDecimal("59900"), null, null));
        // fisico, reservado, stockMinimo=2, ROP opcional
        variante("LIV001-S-NEG", Talla.S, 0, 0, null, true);   // disponible 0  -> AGOTADO
        variante("LIV001-M-NEG", Talla.M, 5, 3, null, true);   // disponible 2  <= min 2 -> BAJO
        variante("LIV001-L-NEG", Talla.L, 10, 0, null, true);  // disponible 10 -> OK
        variante("LIV001-XL-NEG", Talla.XL, 10, 0, 12, true);  // disponible 10 <= ROP 12 -> BAJO
        variante("LIV001-XXL-NEG", Talla.XXL, 10, 0, null, false); // inactiva, OK
    }

    private void variante(String sku, Talla talla, int fisico, int reservado, Integer rop, boolean activa) {
        Variante v = variantes.saveAndFlush(new Variante(producto, sku, talla, "NEGRO", 2));
        jdbc.update("update variante set stock_fisico = ?, stock_reservado = ?, punto_reorden = ?, activo = ? "
                + "where id = ?", fisico, reservado, rop, activa, v.getId());
    }

    private List<String> skus(String q, Long cat, EstadoStock estado, boolean inactivas) {
        return inventario.buscar(ProductoRepository.patronContiene(q), cat, estado, inactivas,
                PageRequest.of(0, 20, Sort.by("sku"))).map(Variante::getSku).getContent();
    }

    @Test
    void buscar_estadoAgotado_devuelveSoloSinDisponible() {
        assertThat(skus(null, null, EstadoStock.AGOTADO, false)).containsExactly("LIV001-S-NEG");
    }

    @Test
    void buscar_estadoBajo_usaRopOStockMinimo() {
        assertThat(skus(null, null, EstadoStock.BAJO, false)).containsExactly("LIV001-M-NEG", "LIV001-XL-NEG");
    }

    @Test
    void buscar_estadoOk_excluyeAgotadoYBajo() {
        assertThat(skus(null, null, EstadoStock.OK, false)).containsExactly("LIV001-L-NEG");
    }

    @Test
    void buscar_inactivas_seExcluyenSalvoQueSePidan() {
        assertThat(skus(null, null, null, false)).hasSize(4).doesNotContain("LIV001-XXL-NEG");
        assertThat(skus(null, null, null, true)).hasSize(5).contains("LIV001-XXL-NEG");
    }

    @Test
    void buscar_textoYCategoria_filtranPorSkuCodigoNombreYEscapaComodines() {
        assertThat(skus("xl-neg", null, null, false)).containsExactly("LIV001-XL-NEG");
        assertThat(skus("liv001", null, null, false)).hasSize(4);
        assertThat(skus("basic", null, null, false)).hasSize(4);
        assertThat(skus("%", null, null, false)).isEmpty();
        assertThat(skus(null, categoria.getId() + 999, null, false)).isEmpty();
        assertThat(skus(null, categoria.getId(), null, false)).hasSize(4);
    }

    @Test
    void buscar_ordenPorNombreDeProducto_ypaginaConConteoCorrecto() {
        var pagina = inventario.buscar(null, null, null, false,
                PageRequest.of(0, 2, Sort.by("producto.nombre", "sku")));

        assertThat(pagina.getTotalElements()).isEqualTo(4);
        assertThat(pagina.getContent()).extracting(Variante::getSku)
                .containsExactly("LIV001-L-NEG", "LIV001-M-NEG");
    }
}
