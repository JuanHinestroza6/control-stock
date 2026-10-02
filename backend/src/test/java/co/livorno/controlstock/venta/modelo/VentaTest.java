package co.livorno.controlstock.venta.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VentaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 2, 10, 0);

    Usuario vendedor = new Usuario("Ana", "ana@livorno.co", "hash", Rol.VENDEDOR);
    Usuario admin = new Usuario("Root", "root@livorno.co", "hash", Rol.ADMIN);
    Variante m;
    Variante l;
    Venta venta;

    @BeforeEach
    void preparar() {
        Producto p = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        m = new Variante(p, "LIV001-M-NEG", Talla.M, "NEGRO", 1);
        l = new Variante(p, "LIV001-L-NEG", Talla.L, "NEGRO", 1);
        venta = new Venta(vendedor, AHORA);
    }

    @Test
    void agregarLinea_conDescuento_calculaTotalYDescuento() {
        // dado dos líneas, una con descuento
        venta.agregarLinea(m, 2, new BigDecimal("59900"), new BigDecimal("50000"), new BigDecimal("20000"));
        venta.agregarLinea(l, 1, new BigDecimal("59900"), new BigDecimal("59900"), new BigDecimal("20000"));

        // entonces
        assertThat(venta.getTotal()).isEqualByComparingTo("159900.00");
        assertThat(venta.descuentoTotal()).isEqualByComparingTo("19800.00");
        assertThat(venta.unidades()).isEqualTo(3);
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.REGISTRADA);
        assertThat(venta.getDetalles()).hasSize(2);
        assertThat(venta.getDetalles().get(0).getPrecioLista().scale()).isEqualTo(2);
    }

    @Test
    void agregarLinea_precioUnitarioMayorQueLista_lanzaExcepcion() {
        assertThatThrownBy(() -> venta.agregarLinea(m, 1, new BigDecimal("59900"), new BigDecimal("60000"),
                BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agregarLinea_cantidadCeroOVarianteRepetida_lanzaExcepcion() {
        assertThatThrownBy(() -> venta.agregarLinea(m, 0, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        venta.agregarLinea(m, 1, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);
        assertThatThrownBy(() -> venta.agregarLinea(m, 1, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anular_ventaRegistrada_quedaAnuladaConDatos() {
        venta.agregarLinea(m, 1, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);

        venta.anular(admin, AHORA.plusHours(1), "  error de digitación ");

        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(venta.getAnuladaPor()).isSameAs(admin);
        assertThat(venta.getAnuladaEn()).isEqualTo(AHORA.plusHours(1));
        assertThat(venta.getMotivoAnulacion()).isEqualTo("error de digitación");
    }

    @Test
    void anular_ventaYaAnulada_lanzaConflicto() {
        venta.anular(admin, AHORA, "motivo uno");

        assertThatThrownBy(() -> venta.anular(admin, AHORA, "motivo dos"))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("ya está anulada");
    }

    @Test
    void numero_sinPersistirEsNulo() {
        assertThat(venta.numero()).isNull();
    }
}
