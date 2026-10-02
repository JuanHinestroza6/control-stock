package co.livorno.controlstock.catalogo.servicio;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Genera SKU con el formato {@code {codigoProducto}-{talla}-{COLOR3}} (ej. LIV001-M-NEG). Clase pura: sin acceso a
 * base de datos; la existencia de colisiones se consulta mediante un predicado.
 */
public final class GeneradorSku {

    private static final int LETRAS_COLOR = 3;

    private GeneradorSku() {}

    /** Color en mayúsculas, sin espacios sobrantes (se conservan las tildes). */
    public static String normalizarColor(String color) {
        if (color == null) {
            throw new IllegalArgumentException("El color es obligatorio");
        }
        String normalizado = color.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            throw new IllegalArgumentException("El color es obligatorio");
        }
        return normalizado;
    }

    /** Primeras 3 letras/dígitos del color, sin tildes, espacios ni la ñ (MARRÓN -> MAR, AZUL CIELO -> AZU). */
    public static String abreviarColor(String color) {
        String sinTildes = Normalizer.normalize(normalizarColor(color), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Z0-9]", "");
        if (sinTildes.isEmpty()) {
            throw new IllegalArgumentException("El color debe contener letras o números");
        }
        return sinTildes.substring(0, Math.min(LETRAS_COLOR, sinTildes.length()));
    }

    /**
     * Devuelve el primer SKU libre: base, base2, base3... (el sufijo numérico va tras el COLOR3).
     *
     * @param existe indica si un SKU candidato ya está en uso (en base de datos o en el lote que se está generando)
     */
    public static String generar(String codigoProducto, String talla, String color, Predicate<String> existe) {
        String base = codigoProducto.strip().toUpperCase(Locale.ROOT) + "-" + talla.strip().toUpperCase(Locale.ROOT)
                + "-" + abreviarColor(color);
        String candidato = base;
        int sufijo = 2;
        while (existe.test(candidato)) {
            candidato = base + sufijo++;
        }
        return candidato;
    }
}
