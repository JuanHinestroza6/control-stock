package co.livorno.controlstock.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.servicio.GeneradorSku;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Pruebas puras del catálogo fijo de demostración (docs/especificaciones/datos-semilla.md). */
class CatalogoSemillaTest {

    private static final List<ProductoSemilla> PRODUCTOS = CatalogoSemilla.productos();

    /** Rango de precio de venta por categoría (COP): docs/especificaciones/datos-semilla.md y plan de la Fase 4. */
    private static final Map<String, long[]> RANGO_PRECIO = Map.of(
            "Camisetas", new long[] {59_900, 89_900},
            "Hoodies", new long[] {129_900, 179_900},
            "Jeans", new long[] {119_900, 169_900},
            "Joggers", new long[] {89_900, 129_900},
            "Chaquetas", new long[] {159_900, 249_900},
            "Accesorios", new long[] {29_900, 69_900});

    @Test
    void treintaProductos_conCodigoUnicoYValido() {
        assertThat(PRODUCTOS).hasSize(30);
        assertThat(PRODUCTOS).extracting(ProductoSemilla::codigo)
                .doesNotHaveDuplicates()
                .allMatch(c -> c.matches("[A-Z0-9-]{2,20}"))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 30).mapToObj(i -> "LIV%03d".formatted(i)).toList());
        assertThat(PRODUCTOS).extracting(ProductoSemilla::nombre)
                .doesNotHaveDuplicates()
                .allMatch(n -> !n.isBlank() && n.length() <= 120);
    }

    @Test
    void categorias_sonLasSeisDeV3_yTodasSeUsan() {
        assertThat(CatalogoSemilla.CATEGORIAS)
                .containsExactly("Camisetas", "Hoodies", "Jeans", "Joggers", "Chaquetas", "Accesorios");
        assertThat(PRODUCTOS).extracting(ProductoSemilla::categoria)
                .allMatch(CatalogoSemilla.CATEGORIAS::contains)
                .containsAll(CatalogoSemilla.CATEGORIAS);
    }

    @Test
    void variantes_unasTrescientas_tallasSaXL_yUnicaEnAccesorios() {
        // 26 prendas × 4 tallas × 72 colores en total = 288; 4 accesorios × UNICA × 11 colores = 11 → 299.
        int total = PRODUCTOS.stream().mapToInt(ProductoSemilla::totalVariantes).sum();
        assertThat(total).isEqualTo(299);
        for (ProductoSemilla p : PRODUCTOS) {
            List<Talla> esperadas = p.esAccesorio()
                    ? List.of(Talla.UNICA)
                    : List.of(Talla.S, Talla.M, Talla.L, Talla.XL);
            assertThat(p.tallas()).as(p.codigo()).isEqualTo(esperadas);
        }
    }

    @Test
    void precios_enRangoPorCategoria_terminacion900_yEscala2() {
        for (ProductoSemilla p : PRODUCTOS) {
            long[] rango = RANGO_PRECIO.get(p.categoria());
            assertThat(p.precioVenta()).as(p.codigo())
                    .isBetween(BigDecimal.valueOf(rango[0]), BigDecimal.valueOf(rango[1]));
            assertThat(p.precioVenta().remainder(BigDecimal.valueOf(1000))).as(p.codigo())
                    .isEqualByComparingTo("900");
            assertThat(p.precioVenta().scale()).isEqualTo(2);
            assertThat(p.costoBase().scale()).isEqualTo(2);
        }
    }

    @Test
    void costo_entre40y55PorCientoDelPrecio_redondeadoACentena() {
        // Ejemplo LIV001: 31.500 / 69.900 = 0,4506 → 45,1 %.
        for (ProductoSemilla p : PRODUCTOS) {
            BigDecimal proporcion = p.costoBase().divide(p.precioVenta(), 4, RoundingMode.HALF_UP);
            assertThat(proporcion).as(p.codigo()).isBetween(new BigDecimal("0.40"), new BigDecimal("0.55"));
            assertThat(p.costoBase().remainder(BigDecimal.valueOf(100))).as(p.codigo()).isEqualByComparingTo("0");
        }
    }

    @Test
    void leadTimeYLote_enRango() {
        assertThat(PRODUCTOS).allSatisfy(p -> {
            assertThat(p.leadTimeDias()).as(p.codigo()).isBetween(10, 30);
            assertThat(p.loteMinimo()).as(p.codigo()).isBetween(1, 3);
        });
    }

    @Test
    void colores_deDosACuatro_normalizados_ySinChoqueDeSku() {
        for (ProductoSemilla p : PRODUCTOS) {
            assertThat(p.colores()).as(p.codigo()).hasSizeBetween(2, 4).doesNotHaveDuplicates();
            for (String color : p.colores()) {
                // Mismo formato que GenerarVariantesRequest y que guarda el servidor (normalizado).
                assertThat(color).matches("^[\\p{L}\\p{N} ]{1,30}$");
                assertThat(GeneradorSku.normalizarColor(color)).isEqualTo(color);
            }
            // COLOR3 distinto dentro del producto: los SKU salen sin sufijo numérico (LIV001-M-NEG, no NEG2).
            assertThat(p.colores().stream().map(GeneradorSku::abreviarColor).toList()).as(p.codigo())
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void rangosDeDemanda_sonUnaPermutacionDe1a30_mezcladaEntreCategorias() {
        assertThat(PRODUCTOS).extracting(ProductoSemilla::rangoDemanda)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 30).boxed().toList());
        // El 20 % superior (rangos 1–6) incluye al menos 3 categorías distintas.
        Map<String, Long> top = PRODUCTOS.stream().filter(p -> p.rangoDemanda() <= 6)
                .collect(Collectors.groupingBy(ProductoSemilla::categoria, Collectors.counting()));
        assertThat(top).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void estancadosYDescontinuado_fueraDelTop_yDisjuntos() {
        List<ProductoSemilla> estancados = PRODUCTOS.stream().filter(ProductoSemilla::estancado).toList();
        List<ProductoSemilla> descontinuados = PRODUCTOS.stream().filter(ProductoSemilla::descontinuado).toList();
        assertThat(estancados).extracting(ProductoSemilla::codigo).containsExactly("LIV004", "LIV026", "LIV030");
        assertThat(descontinuados).extracting(ProductoSemilla::codigo).containsExactly("LIV017");
        assertThat(estancados).allMatch(p -> p.rangoDemanda() > 6 && !p.descontinuado());
        assertThat(descontinuados).allMatch(p -> p.rangoDemanda() > 6);
    }

    @Test
    void listaInmutable() {
        assertThatThrownBy(() -> PRODUCTOS.add(PRODUCTOS.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PRODUCTOS.getFirst().colores().add("ROJO"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void productoSemilla_validaSusDatos() {
        BigDecimal precio = new BigDecimal("59900.00");
        BigDecimal costo = new BigDecimal("26900.00");
        List<String> dos = List.of("NEGRO", "BLANCO");
        assertThatThrownBy(() -> new ProductoSemilla("X01", "X", "Camisetas", precio, costo, List.of("NEGRO"), 12, 1,
                1, false, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductoSemilla("X01", "X", "Camisetas", costo, precio, dos, 12, 1, 1, false,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductoSemilla("X01", "X", "Camisetas", precio, costo, dos, 0, 1, 1, false,
                false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductoSemilla("X01", "X", "Camisetas", precio, costo, dos, 12, 1, 1, true,
                true)).isInstanceOf(IllegalArgumentException.class);
    }
}
