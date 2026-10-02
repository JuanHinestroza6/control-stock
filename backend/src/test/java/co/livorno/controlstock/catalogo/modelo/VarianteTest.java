package co.livorno.controlstock.catalogo.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Version;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VarianteTest {

    private Variante variante(int stockMinimo) {
        Producto p = new Producto("LIV001", "Camiseta", new Categoria("Camisetas"), new BigDecimal("20000"),
                new BigDecimal("59900"), null, null);
        return new Variante(p, "LIV001-M-NEG", Talla.M, "  negro ", stockMinimo);
    }

    @Test
    void nueva_nacenEnCeroYActivaConColorNormalizado() {
        Variante v = variante(3);

        assertThat(v.getStockFisico()).isZero();
        assertThat(v.getStockReservado()).isZero();
        assertThat(v.getStockDisponible()).isZero();
        assertThat(v.isActivo()).isTrue();
        assertThat(v.getColor()).isEqualTo("NEGRO");
        assertThat(v.getPuntoReorden()).isNull();
        assertThat(v.getClaseAbc()).isNull();
    }

    @Test
    void reglaDeOro1_ningunMetodoPublicoCambiaElStock() {
        // Ningún setter ni mutador público toca stockFisico/stockReservado (solo MovimientoService en Fase 2).
        Method[] publicos = Variante.class.getDeclaredMethods();
        assertThat(Arrays.stream(publicos).filter(m -> Modifier.isPublic(m.getModifiers())))
                .extracting(Method::getName)
                .noneMatch(n -> n.startsWith("set"))
                .noneMatch(n -> n.toLowerCase().contains("stockfisico") && !n.equals("getStockFisico"))
                .noneMatch(n -> n.toLowerCase().contains("stockreservado") && !n.equals("getStockReservado"))
                .doesNotContain("aplicar");
    }

    @Test
    void version_estaAnotadaConVersionParaBloqueoOptimista() throws NoSuchFieldException {
        assertThat(Variante.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void estadoStock_disponibleCero_esAgotado() {
        assertThat(variante(0).estadoStock()).isEqualTo(EstadoStock.AGOTADO);
        assertThat(variante(5).estadoStock()).isEqualTo(EstadoStock.AGOTADO);
    }

    @Test
    void estadoStock_sinRop_usaStockMinimo() {
        Variante v = variante(5);
        ReflectionTestUtils.setField(v, "stockFisico", 5);
        assertThat(v.estadoStock()).isEqualTo(EstadoStock.BAJO);
        ReflectionTestUtils.setField(v, "stockFisico", 6);
        assertThat(v.estadoStock()).isEqualTo(EstadoStock.OK);
    }

    @Test
    void estadoStock_conRop_usaRopYDescuentaReservado() {
        Variante v = variante(5);
        ReflectionTestUtils.setField(v, "puntoReorden", 10);
        ReflectionTestUtils.setField(v, "stockFisico", 12);
        ReflectionTestUtils.setField(v, "stockReservado", 2);
        assertThat(v.estadoStock()).isEqualTo(EstadoStock.BAJO);
        ReflectionTestUtils.setField(v, "stockReservado", 1);
        assertThat(v.estadoStock()).isEqualTo(EstadoStock.OK);
    }

    @Test
    void actualizarStockMinimo_negativo_lanza() {
        assertThatThrownBy(() -> variante(1).actualizarStockMinimo(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> variante(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tallas_respetanElOrdenDelDominio() {
        assertThat(Talla.values()).containsExactly(Talla.XS, Talla.S, Talla.M, Talla.L, Talla.XL, Talla.XXL,
                Talla.UNICA);
    }
}
