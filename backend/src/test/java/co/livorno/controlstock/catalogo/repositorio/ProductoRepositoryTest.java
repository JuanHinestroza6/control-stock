package co.livorno.controlstock.catalogo.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.servicio.CategoriaService;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class, CategoriaService.class})
@ActiveProfiles("test")
class ProductoRepositoryTest {

    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired CategoriaService categoriaService;
    @Autowired JdbcTemplate jdbc;

    Categoria camisetas;
    Categoria jeans;

    @BeforeEach
    void preparar() {
        camisetas = categorias.findAllByOrderByNombreAsc().stream()
                .filter(c -> c.getNombre().equals("Camisetas")).findFirst().orElseThrow();
        jeans = categorias.findAllByOrderByNombreAsc().stream()
                .filter(c -> c.getNombre().equals("Jeans")).findFirst().orElseThrow();
    }

    private Producto crear(String codigo, String nombre, Categoria cat) {
        return productos.saveAndFlush(new Producto(codigo, nombre, cat, new BigDecimal("20000"),
                new BigDecimal("59900"), null, null));
    }

    @Test
    void guardar_aplicaDefaultsMayusculasYEscala() {
        Producto p = crear("liv001", "Camiseta Oversize", camisetas);

        assertThat(p.getCodigo()).isEqualTo("LIV001");
        assertThat(p.getLeadTimeDias()).isEqualTo(15);
        assertThat(p.getLoteMinimo()).isEqualTo(1);
        assertThat(p.getPrecioVenta()).isEqualByComparingTo("59900.00");
        assertThat(p.isActivo()).isTrue();
        assertThat(p.getCreadoEn()).isNotNull();
    }

    @Test
    void guardar_codigoDuplicadoIgnorandoMayusculas_violaUnicidad() {
        crear("LIV001", "A", camisetas);

        assertThatThrownBy(() -> crear("liv001", "B", camisetas)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void check_precioVentaCero_loRechazaLaBaseDeDatos() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into producto (codigo, nombre, categoria_id, precio_compra, precio_venta, creado_en, actualizado_en)
                values ('X1', 'x', ?, 0, 0, now(6), now(6))""", camisetas.getId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_producto_precio_venta");
    }

    @Test
    void buscar_filtraPorTextoCategoriaYEstado() {
        crear("LIV001", "Camiseta Oversize", camisetas);
        Producto b = crear("LIV002", "Jean Recto", jeans);
        crear("LIV003", "Camiseta Basic", camisetas).desactivar();
        productos.flush();
        var pag = PageRequest.of(0, 20, Sort.by("nombre"));

        assertThat(productos.buscar(null, null, null, pag).getTotalElements()).isEqualTo(3);
        assertThat(productos.buscar(ProductoRepository.patronContiene("camiseta"), null, null, pag).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIV003", "LIV001");
        assertThat(productos.buscar(null, jeans.getId(), null, pag).getContent()).containsExactly(b);
        assertThat(productos.buscar(null, null, true, pag).getTotalElements()).isEqualTo(2);
        assertThat(productos.buscar(ProductoRepository.patronContiene("liv00"), camisetas.getId(), false, pag)
                .getContent()).extracting(Producto::getCodigo).containsExactly("LIV003");
    }

    @Test
    void buscar_percentYGuionBajo_seTratanComoTextoLiteral() {
        crear("LIV010", "Descuento 50% oferta", camisetas);
        crear("LIV011", "Camiseta A_B", camisetas);
        crear("LIV012", "Camiseta AxB", camisetas);
        var pag = PageRequest.of(0, 20);

        assertThat(productos.buscar(ProductoRepository.patronContiene("50%"), null, null, pag).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIV010");
        assertThat(productos.buscar(ProductoRepository.patronContiene("A_B"), null, null, pag).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIV011");
        assertThat(productos.buscar(ProductoRepository.patronContiene("%"), null, null, pag).getContent())
                .extracting(Producto::getCodigo).containsExactly("LIV010");
    }

    @Test
    void buscar_paginaYOrdena() {
        for (int i = 1; i <= 5; i++) {
            crear("P0" + i, "Prod " + i, camisetas);
        }

        var pagina = productos.buscar(null, null, null, PageRequest.of(1, 2, Sort.by("codigo").descending()));

        assertThat(pagina.getTotalElements()).isEqualTo(5);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(Producto::getCodigo).containsExactly("P03", "P02");
    }

    @Test
    void contarPorCategoria_yListarCategorias_devuelvenTotalesReales() {
        crear("LIV001", "A", camisetas);
        crear("LIV002", "B", camisetas);
        crear("LIV003", "C", jeans);

        assertThat(productos.contarPorCategoria()).contains(
                new ConteoPorCategoria(camisetas.getId(), 2), new ConteoPorCategoria(jeans.getId(), 1));
        assertThat(categoriaService.listar()).filteredOn(c -> c.nombre().equals("Camisetas"))
                .extracting("totalProductos").containsExactly(2L);
        assertThat(categoriaService.listar()).filteredOn(c -> c.nombre().equals("Hoodies"))
                .extracting("totalProductos").containsExactly(0L);
    }

    @Test
    void eliminarCategoria_conProductos_lanzaConflicto() {
        crear("LIV001", "A", jeans);

        assertThatThrownBy(() -> categoriaService.eliminar(jeans.getId()))
                .isInstanceOf(ConflictoException.class)
                .hasMessage("La categoría tiene productos asociados");
    }

    @Test
    void eliminarCategoria_sinProductos_laBorra() {
        categoriaService.eliminar(jeans.getId());

        assertThat(categorias.existsById(jeans.getId())).isFalse();
    }

    @Test
    void borradoFisicoConProductos_lorechazaLaFk() {
        crear("LIV001", "A", jeans);

        assertThatThrownBy(() -> jdbc.update("delete from categoria where id = ?", jeans.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
