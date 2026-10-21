package co.livorno.controlstock.analitica.repositorio;

import java.time.YearMonth;

/**
 * Fila dispersa de demanda de inventario mensual de un producto: neto del mes ya recortado a 0 (vendidas - devueltas
 * aptas). Los meses sin filas valen 0; {@code SerieMensual.construir} los rellena.
 */
public record DemandaMensualProducto(long productoId, YearMonth mes, int neto) {
}
