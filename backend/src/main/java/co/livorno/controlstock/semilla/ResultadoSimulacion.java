package co.livorno.controlstock.semilla;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conteos de la simulación diaria. {@code unidadesPerdidas}: unidades pedidas por clientes que no se vendieron por
 * falta de disponible (demanda censurada). {@code unidadesPerdidasComparables}: las mismas, pero solo las que la
 * corrección por agotados de la analítica puede ver (ADR-011, D5): los 12 meses completos antes del mes del corte, sin
 * las variantes que se dejan agotar a propósito ni el producto descontinuado. {@code enCaminoPorVariante}: unidades en planes APROBADO al terminar,
 * en el orden de {@code CatalogoCreado.variantes()} (no depende de los ids de la base). {@code ingreso} excluye las ventas anuladas.
 */
public record ResultadoSimulacion(int dias, long ventas, long unidadesVendidas, long lineasPedidas,
                                  long lineasPerdidas, long lineasEvitablesPedidas, long lineasEvitablesPerdidas,
                                  long unidadesPerdidas, long unidadesPerdidasComparables, long anuladas, long devoluciones, long devolucionesNoAptas,
                                  long entradas, long ajustes, int alzasDePrecio, int productosDesactivados,
                                  BigDecimal ingreso, long reservasCreadas, long reservasConvertidas,
                                  long reservasCanceladas, long reservasVencidas, long planesCreados,
                                  long planesRecibidos, long planesEnCamino, long unidadesEnCamino,
                                  List<Integer> enCaminoPorVariante) {

    /** % de líneas perdidas sin contar las variantes que se dejan agotar a propósito ni el descontinuado. */
    public double porcentajePerdidasEvitables() {
        return lineasEvitablesPedidas == 0 ? 0 : 100.0 * lineasEvitablesPerdidas / lineasEvitablesPedidas;
    }
}
