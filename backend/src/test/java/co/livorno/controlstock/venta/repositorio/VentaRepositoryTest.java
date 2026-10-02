package co.livorno.controlstock.venta.repositorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.TestcontainersConfiguration;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.modelo.Venta;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, co.livorno.controlstock.comun.config.ConfiguracionTiempo.class,
        co.livorno.controlstock.comun.config.ConfiguracionAuditoria.class})
@ActiveProfiles("test")
class VentaRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Autowired VentaRepository ventas;
    @Autowired UsuarioRepository usuarios;
    @Autowired VarianteRepository variantes;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    Usuario vendedor;
    Variante variante;

    @BeforeEach
    void preparar() {
        vendedor = usuarios.saveAndFlush(new Usuario("Ana", "ana.venta@livorno.co", "hash", Rol.VENDEDOR));
        Producto p = productos.saveAndFlush(new Producto("LIV001", "Camiseta",
                categorias.findAllByOrderByNombreAsc().get(0), new BigDecimal("20000"), new BigDecimal("59900"),
                null, null));
        variante = variantes.saveAndFlush(new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1));
    }

    @Test
    void save_ventaConLineas_persisteEnCascadaYRecuperaPrecios() {
        Venta v = new Venta(vendedor, AHORA);
        v.agregarLinea(variante, 2, new BigDecimal("59900"), new BigDecimal("50000"), new BigDecimal("20000"));

        Long id = ventas.saveAndFlush(v).getId();
        em.clear();

        Venta leida = ventas.findById(id).orElseThrow();
        assertThat(leida.numero()).isEqualTo(String.format("V-%06d", id));
        assertThat(leida.getTotal()).isEqualByComparingTo("100000.00");
        assertThat(leida.getDetalles()).hasSize(1);
        assertThat(leida.getDetalles().get(0).getPrecioLista()).isEqualByComparingTo("59900.00");
        assertThat(leida.getDetalles().get(0).getCostoUnitario()).isEqualByComparingTo("20000.00");
    }

    @Test
    void anular_ventaPersistida_guardaEstadoYAuditoriaDeAnulacion() {
        Venta v = new Venta(vendedor, AHORA);
        v.agregarLinea(variante, 1, new BigDecimal("59900"), new BigDecimal("59900"), BigDecimal.ZERO);
        Long id = ventas.saveAndFlush(v).getId();

        v.anular(vendedor, AHORA.plusMinutes(5), "error de captura");
        ventas.saveAndFlush(v);
        em.clear();

        Venta leida = ventas.findById(id).orElseThrow();
        assertThat(leida.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(leida.getAnuladaEn()).isEqualTo(AHORA.plusMinutes(5));
        assertThat(leida.getMotivoAnulacion()).isEqualTo("error de captura");
    }

    @Test
    void bloquear_ventaExistente_laDevuelveYInexistenteVacio() {
        Venta v = new Venta(vendedor, AHORA);
        v.agregarLinea(variante, 1, new BigDecimal("59900"), new BigDecimal("59900"), BigDecimal.ZERO);
        Long id = ventas.saveAndFlush(v).getId();
        em.clear();

        assertThat(ventas.bloquear(id)).isPresent();
        assertThat(ventas.bloquear(id + 999)).isEmpty();
    }

    @Test
    void check_filasInvalidas_seRechazan() {
        // estado ANULADA sin anulada_en
        assertThatThrownBy(() -> jdbc.update("""
                insert into venta (fecha, usuario_id, total, estado) values (now(6), ?, 10.00, 'ANULADA')""",
                vendedor.getId())).isInstanceOf(DataAccessException.class);
        // total negativo
        assertThatThrownBy(() -> jdbc.update("""
                insert into venta (fecha, usuario_id, total, estado) values (now(6), ?, -1.00, 'REGISTRADA')""",
                vendedor.getId())).isInstanceOf(DataAccessException.class);

        Venta v = new Venta(vendedor, AHORA);
        v.agregarLinea(variante, 1, new BigDecimal("59900"), new BigDecimal("59900"), BigDecimal.ZERO);
        Long id = ventas.saveAndFlush(v).getId();
        // precio unitario mayor que la lista
        assertThatThrownBy(() -> jdbc.update("""
                insert into detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,
                    costo_unitario) values (?, ?, 1, 100.00, 200.00, 0.00)""", id, variante.getId() + 1000))
                .isInstanceOf(DataAccessException.class);
        // variante repetida en la misma venta
        assertThatThrownBy(() -> jdbc.update("""
                insert into detalle_venta (venta_id, variante_id, cantidad, precio_lista, precio_unitario,
                    costo_unitario) values (?, ?, 1, 100.00, 100.00, 0.00)""", id, variante.getId()))
                .isInstanceOf(DataAccessException.class);
    }
}
