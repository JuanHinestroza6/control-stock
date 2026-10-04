package co.livorno.controlstock.semilla;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo fijo de demostración: 30 productos, 299 variantes (skill {@code datos-semilla}, ADR-008).
 *
 * <p>Las 6 categorías son las de la migración V3 y se buscan por nombre. Precios en COP con terminación 900; costo entre
 * 40 % y 55 % del precio. {@code rangoDemanda} es una permutación de 1..30 mezclada entre categorías: los 6 primeros
 * (20 %) concentran ≈ 77 % de las unidades (ver {@link ModeloDemanda#EXPONENTE_ZIPF}). Estancados: LIV004, LIV026 y
 * LIV030. Descontinuado: LIV017 (jean skinny, pasado de moda).
 *
 * <p>Clase de datos sin Spring: el generador la usa directamente. El orden de la lista es fijo (determinismo).
 */
public final class CatalogoSemilla {

    private static final String CAMISETAS = "Camisetas";
    private static final String HOODIES = "Hoodies";
    private static final String JEANS = "Jeans";
    private static final String JOGGERS = "Joggers";
    private static final String CHAQUETAS = "Chaquetas";
    private static final String ACCESORIOS = ProductoSemilla.CATEGORIA_ACCESORIOS;

    /** Categorías base de la migración V3, en su orden de inserción. */
    public static final List<String> CATEGORIAS = List.of(CAMISETAS, HOODIES, JEANS, JOGGERS, CHAQUETAS, ACCESORIOS);

    private static final List<ProductoSemilla> PRODUCTOS = List.of(
            // Camisetas: 59.900–89.900, reposición 12–20 días
            p("LIV001", "Camiseta Oversize Basic", CAMISETAS, 69_900, 31_500,
                    List.of("NEGRO", "BLANCO", "GRIS", "BEIGE"), 12, 3, 1),
            p("LIV002", "Camiseta Boxy Estampada", CAMISETAS, 79_900, 39_900,
                    List.of("NEGRO", "BLANCO", "VERDE OLIVA"), 15, 3, 4),
            p("LIV003", "Camiseta Polo Piqué", CAMISETAS, 89_900, 44_900,
                    List.of("AZUL NOCHE", "BLANCO", "NEGRO"), 20, 2, 18),
            estancado(p("LIV004", "Camiseta Tie Dye", CAMISETAS, 74_900, 33_700,
                    List.of("MULTICOLOR", "AZUL"), 15, 2, 20)),
            p("LIV005", "Camiseta Manga Larga Rib", CAMISETAS, 64_900, 27_300,
                    List.of("NEGRO", "BLANCO", "MARRÓN"), 12, 3, 11),
            p("LIV006", "Camiseta Gráfica Medellín", CAMISETAS, 79_900, 35_900,
                    List.of("NEGRO", "BLANCO"), 12, 3, 7),
            p("LIV007", "Camiseta Crop Fit", CAMISETAS, 59_900, 26_900,
                    List.of("NEGRO", "BLANCO", "ROSA", "LILA"), 14, 3, 13),
            p("LIV008", "Camiseta Henley", CAMISETAS, 69_900, 31_400,
                    List.of("GRIS", "AZUL", "BEIGE"), 18, 2, 23),
            // Hoodies: 129.900–179.900, reposición 18–22 días
            p("LIV009", "Hoodie Oversize Basic", HOODIES, 149_900, 67_400,
                    List.of("NEGRO", "GRIS", "BEIGE", "AZUL"), 20, 2, 2),
            p("LIV010", "Hoodie Estampado Livorno", HOODIES, 169_900, 84_900,
                    List.of("NEGRO", "BLANCO"), 22, 2, 8),
            p("LIV011", "Hoodie Zipper", HOODIES, 159_900, 71_900,
                    List.of("GRIS", "NEGRO", "VERDE"), 20, 2, 12),
            p("LIV012", "Hoodie Cropped", HOODIES, 129_900, 57_900,
                    List.of("ROSA", "NEGRO", "LILA"), 18, 2, 21),
            p("LIV013", "Buzo Crewneck", HOODIES, 139_900, 62_900,
                    List.of("GRIS", "AZUL"), 20, 2, 24),
            // Jeans: 119.900–169.900, reposición 25 días
            p("LIV014", "Jean Baggy", JEANS, 149_900, 67_400,
                    List.of("CELESTE", "ÍNDIGO", "NEGRO"), 25, 2, 3),
            p("LIV015", "Jean Mom Fit", JEANS, 139_900, 62_900,
                    List.of("CELESTE", "ÍNDIGO"), 25, 2, 9),
            p("LIV016", "Jean Recto Clásico", JEANS, 129_900, 58_400,
                    List.of("ÍNDIGO", "NEGRO", "GRIS"), 25, 2, 14),
            descontinuado(p("LIV017", "Jean Skinny", JEANS, 119_900, 53_900,
                    List.of("ÍNDIGO", "NEGRO"), 25, 2, 16)),
            p("LIV018", "Jean Cargo Wide Leg", JEANS, 169_900, 84_900,
                    List.of("NEGRO", "BEIGE", "CELESTE"), 25, 2, 17),
            // Joggers: 99.900–129.900, reposición 15–18 días
            p("LIV019", "Jogger Cargo", JOGGERS, 119_900, 53_900,
                    List.of("NEGRO", "VERDE OLIVA", "BEIGE"), 15, 2, 5),
            p("LIV020", "Jogger Basic Algodón", JOGGERS, 99_900, 44_900,
                    List.of("NEGRO", "GRIS", "AZUL"), 15, 2, 6),
            p("LIV021", "Jogger Nylon", JOGGERS, 109_900, 49_400,
                    List.of("NEGRO", "AZUL"), 18, 2, 19),
            p("LIV022", "Pantalón Parachute", JOGGERS, 129_900, 58_400,
                    List.of("NEGRO", "GRIS", "VERDE"), 18, 2, 27),
            // Chaquetas: 179.900–239.900, reposición 30 días
            p("LIV023", "Chaqueta Denim", CHAQUETAS, 199_900, 89_900,
                    List.of("ÍNDIGO", "NEGRO", "CELESTE"), 30, 1, 22),
            p("LIV024", "Chaqueta Bomber", CHAQUETAS, 219_900, 98_900,
                    List.of("NEGRO", "VERDE OLIVA"), 30, 1, 26),
            p("LIV025", "Chaqueta Cortavientos", CHAQUETAS, 179_900, 80_900,
                    List.of("NEGRO", "AZUL", "ROJO"), 30, 1, 29),
            estancado(p("LIV026", "Chaqueta de Pana", CHAQUETAS, 239_900, 107_900,
                    List.of("MARRÓN", "BEIGE"), 30, 1, 28)),
            // Accesorios (talla UNICA): 29.900–69.900, reposición 10 días
            p("LIV027", "Gorra Livorno", ACCESORIOS, 49_900, 21_900,
                    List.of("NEGRO", "BEIGE", "BLANCO"), 10, 3, 10),
            p("LIV028", "Medias Pack x3", ACCESORIOS, 29_900, 12_900,
                    List.of("BLANCO", "NEGRO", "GRIS"), 10, 3, 15),
            p("LIV029", "Riñonera Urbana", ACCESORIOS, 69_900, 30_900,
                    List.of("NEGRO", "BEIGE"), 10, 2, 25),
            estancado(p("LIV030", "Bucket Hat", ACCESORIOS, 39_900, 17_400,
                    List.of("NEGRO", "BEIGE", "AZUL"), 10, 3, 30)));

    private CatalogoSemilla() {
    }

    /** Los 30 productos en orden de código. Lista inmutable. */
    public static List<ProductoSemilla> productos() {
        return PRODUCTOS;
    }

    private static ProductoSemilla p(String codigo, String nombre, String categoria, long precio, long costo,
                                     List<String> colores, int leadTimeDias, int loteMinimo, int rango) {
        return new ProductoSemilla(codigo, nombre, categoria, cop(precio), cop(costo), colores, leadTimeDias,
                loteMinimo, rango, false, false);
    }

    private static ProductoSemilla estancado(ProductoSemilla p) {
        return new ProductoSemilla(p.codigo(), p.nombre(), p.categoria(), p.precioVenta(), p.costoBase(), p.colores(),
                p.leadTimeDias(), p.loteMinimo(), p.rangoDemanda(), true, false);
    }

    private static ProductoSemilla descontinuado(ProductoSemilla p) {
        return new ProductoSemilla(p.codigo(), p.nombre(), p.categoria(), p.precioVenta(), p.costoBase(), p.colores(),
                p.leadTimeDias(), p.loteMinimo(), p.rangoDemanda(), false, true);
    }

    private static BigDecimal cop(long pesos) {
        return BigDecimal.valueOf(pesos).setScale(2);
    }
}
