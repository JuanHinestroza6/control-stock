package co.livorno.controlstock.compra.servicio;

import java.util.List;

/**
 * Puerto que compra usa para pedir la sugerencia (ADR-010): lo implementa analitica, así compra no depende de
 * analitica. Nunca toca stock. El filtro de variantes que ya están en otro BORRADOR lo hace el servicio de compra.
 */
public interface FuenteSugerenciaCompra {

    /** Línea sugerida; {@code cantidad} es múltiplo de loteMinimo y {@code justificacion} cabe en 255. */
    record LineaSugerida(Long varianteId, int cantidad, String justificacion) {
    }

    /** Resultado: líneas por id de variante ascendente y la ejecución analítica de la que salen. */
    record Sugerencia(Long ejecucionId, List<LineaSugerida> lineas) {
    }

    /** Sugerencia para variantes activas de productos activos, con categoría opcional (null = todas). */
    Sugerencia sugerir(Long categoriaId);
}
