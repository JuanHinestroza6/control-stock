package co.livorno.controlstock.semilla;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Conteos de la simulación diaria. {@code unidadesPerdidas}: unidades pedidas por clientes que no se vendieron por
 * falta de disponible (demanda censurada). {@code perdidasEvitablesPorMes}: las mismas por mes, sin las variantes que se
 * dejan agotar a propósito ni el producto descontinuado, para compararlas con la corrección por agotados de la
 * analítica en su misma ventana (ADR-011, D5; ver {@link #unidadesPerdidasComparables}). {@code enCaminoPorVariante}:
 * unidades en planes APROBADO al terminar, en el orden de {@code CatalogoCreado.variantes()} (no depende de los ids de
 * la base). {@code ingreso} excluye las ventas anuladas.
 */
public record ResultadoSimulacion(int dias, long ventas, long unidadesVendidas, long lineasPedidas,
                                  long lineasPerdidas, long lineasEvitablesPedidas, long lineasEvitablesPerdidas,
                                  long unidadesPerdidas, Map<YearMonth, Long> perdidasEvitablesPorMes, long anuladas,
                                  long devoluciones, long devolucionesNoAptas,
                                  long entradas, long ajustes, int alzasDePrecio, int productosDesactivados,
                                  BigDecimal ingreso, long reservasCreadas, long reservasConvertidas,
                                  long reservasCanceladas, long reservasVencidas, long planesCreados,
                                  long planesRecibidos, long planesEnCamino, long unidadesEnCamino,
                                  List<Integer> enCaminoPorVariante) {

    /** % de líneas perdidas sin contar las variantes que se dejan agotar a propósito ni el descontinuado. */
    public double porcentajePerdidasEvitables() {
        return lineasEvitablesPedidas == 0 ? 0 : 100.0 * lineasEvitablesPerdidas / lineasEvitablesPedidas;
    }

    /**
     * Unidades perdidas evitables en los 12 meses completos anteriores a {@code mesCorte}: la misma ventana de la serie
     * que corrige la analítica (meses completos antes del mes de su fecha de corte).
     */
    public long unidadesPerdidasComparables(YearMonth mesCorte) {
        YearMonth inicio = mesCorte.minusMonths(12);
        return perdidasEvitablesPorMes.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(inicio) && e.getKey().isBefore(mesCorte))
                .mapToLong(Map.Entry::getValue).sum();
    }
}
