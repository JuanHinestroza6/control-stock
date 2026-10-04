package co.livorno.controlstock.semilla;

import java.math.BigDecimal;

/**
 * Conteos de la simulación diaria. {@code unidadesPerdidas}: unidades pedidas por clientes que no se vendieron por
 * falta de disponible (demanda censurada). {@code ingreso} excluye las ventas anuladas.
 */
public record ResultadoSimulacion(int dias, long ventas, long unidadesVendidas, long lineasPerdidas,
                                  long unidadesPerdidas, long anuladas, long devoluciones, long devolucionesNoAptas,
                                  long entradas, long ajustes, int alzasDePrecio, int productosDesactivados,
                                  BigDecimal ingreso) {
}
