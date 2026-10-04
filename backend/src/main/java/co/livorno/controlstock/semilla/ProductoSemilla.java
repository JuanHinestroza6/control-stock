package co.livorno.controlstock.semilla;

import co.livorno.controlstock.catalogo.modelo.Talla;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Datos fijos de un producto de demostración (ver {@link CatalogoSemilla}). No es una entidad: el generador lo convierte
 * en un {@code CrearProductoRequest} y un {@code GenerarVariantesRequest}.
 *
 * @param codigo        código del producto ({@code LIV001…})
 * @param nombre        nombre visible
 * @param categoria     nombre de una categoría base de la migración V3 (se busca, no se crea)
 * @param precioVenta   precio de lista inicial en COP (escala 2, terminación 900)
 * @param costoBase     costo de compra inicial en COP (40–55 % del precio, escala 2)
 * @param colores       2 a 4 colores en mayúsculas; el primero es el más vendido (50 %)
 * @param leadTimeDias  tiempo de reposición, 10–30 días
 * @param loteMinimo    lote mínimo de compra por variante
 * @param rangoDemanda  posición en el ranking de demanda (1 = más vendido); define el peso escalonado
 * @param estancado     demanda nula en los últimos 3 meses de la historia (para SIN_ROTACION)
 * @param descontinuado deja de reponerse hace 6 meses, se agota y se desactiva (lo maneja el generador)
 */
public record ProductoSemilla(String codigo, String nombre, String categoria, BigDecimal precioVenta,
                              BigDecimal costoBase, List<String> colores, int leadTimeDias, int loteMinimo,
                              int rangoDemanda, boolean estancado, boolean descontinuado) {

    /** Categoría cuyas variantes usan la talla UNICA. */
    public static final String CATEGORIA_ACCESORIOS = "Accesorios";

    private static final List<Talla> TALLAS_PRENDA = List.of(Talla.S, Talla.M, Talla.L, Talla.XL);
    private static final List<Talla> TALLAS_ACCESORIO = List.of(Talla.UNICA);

    public ProductoSemilla {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(precioVenta, "precioVenta");
        Objects.requireNonNull(costoBase, "costoBase");
        colores = List.copyOf(colores);
        if (colores.size() < 2 || colores.size() > 4) {
            throw new IllegalArgumentException(codigo + ": se esperan de 2 a 4 colores");
        }
        if (precioVenta.signum() <= 0 || costoBase.signum() <= 0 || costoBase.compareTo(precioVenta) >= 0) {
            throw new IllegalArgumentException(codigo + ": se espera 0 < costo < precio");
        }
        if (leadTimeDias < 1 || loteMinimo < 1 || rangoDemanda < 1) {
            throw new IllegalArgumentException(codigo + ": lead time, lote y rango deben ser positivos");
        }
        if (estancado && descontinuado) {
            throw new IllegalArgumentException(codigo + ": un producto no puede ser estancado y descontinuado");
        }
    }

    public boolean esAccesorio() {
        return CATEGORIA_ACCESORIOS.equals(categoria);
    }

    /** S, M, L, XL para prendas; UNICA para accesorios. */
    public List<Talla> tallas() {
        return esAccesorio() ? TALLAS_ACCESORIO : TALLAS_PRENDA;
    }

    public int totalVariantes() {
        return tallas().size() * colores.size();
    }
}
