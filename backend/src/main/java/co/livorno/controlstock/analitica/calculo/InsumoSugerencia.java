package co.livorno.controlstock.analitica.calculo;

/**
 * Situación de una variante activa (de un producto activo) que evalúa la sugerencia de compra
 * ({@link CalculadoraSugerenciaCompra}). El servicio filtra antes las inactivas y las que ya están en otro BORRADOR.
 *
 * @param varianteId            id de la variante (orden de salida)
 * @param puntoReorden          ROP vigente, o null (SIN_HISTORIA: el umbral es {@code stockMinimo})
 * @param stockMinimo           respaldo del umbral cuando no hay ROP, ≥ 0
 * @param disponible            físico − reservado, ≥ 0
 * @param enCamino              Σ cantidadAprobada de las líneas de planes APROBADO, ≥ 0
 * @param leadTimeDias          L del producto, ≥ 0
 * @param loteMinimo            múltiplo de compra del producto, ≥ 1
 * @param pronostico            pronóstico mensual de la variante (desagregado), o null si el producto no tiene pronóstico
 * @param demandaMediaHistorica d̄ observada de la serie diaria de 90 días (unidades/día, ≥ 0); solo se usa sin pronóstico
 */
public record InsumoSugerencia(long varianteId, Integer puntoReorden, int stockMinimo, int disponible, int enCamino,
        int leadTimeDias, int loteMinimo, PronosticoMensual pronostico, double demandaMediaHistorica) {

    public InsumoSugerencia {
        if (puntoReorden != null && puntoReorden < 0) {
            throw new IllegalArgumentException("El punto de reorden no puede ser negativo: " + puntoReorden);
        }
        if (stockMinimo < 0 || disponible < 0 || enCamino < 0 || leadTimeDias < 0) {
            throw new IllegalArgumentException("Stock mínimo, disponible, en camino y lead time no pueden ser negativos.");
        }
        if (loteMinimo < 1) {
            throw new IllegalArgumentException("El lote mínimo debe ser al menos 1: " + loteMinimo);
        }
        if (!(demandaMediaHistorica >= 0.0) || Double.isInfinite(demandaMediaHistorica)) {
            throw new IllegalArgumentException("La demanda media histórica debe ser finita y ≥ 0: "
                    + demandaMediaHistorica);
        }
    }

    /** umbral = puntoReorden ?? stockMinimo. */
    public int umbral() {
        return puntoReorden != null ? puntoReorden : stockMinimo;
    }

    /** posición = disponible + en camino. */
    public long posicion() {
        return (long) disponible + enCamino;
    }
}
