package co.livorno.controlstock.semilla;

import java.math.BigDecimal;

/**
 * Conteos de la simulación diaria. {@code unidadesPerdidas}: unidades pedidas por clientes que no se vendieron por
 * falta de disponible (demanda censurada). {@code ingreso} excluye las ventas anuladas.
 */
public record ResultadoSimulacion(int dias, long ventas, long unidadesVendidas, long lineasPedidas,
                                  long lineasPerdidas, long lineasEvitablesPedidas, long lineasEvitablesPerdidas,
                                  long unidadesPerdidas, long anuladas, long devoluciones, long devolucionesNoAptas,
                                  long entradas, long ajustes, int alzasDePrecio, int productosDesactivados,
                                  BigDecimal ingreso) {

    /** % de líneas perdidas sin contar las variantes que se dejan agotar a propósito ni el descontinuado. */
    public double porcentajePerdidasEvitables() {
        return lineasEvitablesPedidas == 0 ? 0 : 100.0 * lineasEvitablesPerdidas / lineasEvitablesPedidas;
    }
}
