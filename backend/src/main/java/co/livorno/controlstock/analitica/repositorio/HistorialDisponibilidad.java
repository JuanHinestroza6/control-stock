package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.calculo.SaldoDisponible;
import java.time.LocalDate;
import java.util.List;

/**
 * Insumo de {@code CorrectorCensura} para una variante activa de un producto activo: su dia de creacion y el disponible
 * (fisico - reservado) tras cada movimiento del kardex, en orden (fecha, id). Incluye el ultimo saldo anterior al
 * inicio de la ventana pedida (fija el disponible con que abre el primer dia) y todos los de la ventana; una variante
 * sin movimientos trae la lista vacia (siempre sin stock).
 */
public record HistorialDisponibilidad(long varianteId, long productoId, LocalDate creadaEn,
        List<SaldoDisponible> saldos) {

    public HistorialDisponibilidad {
        saldos = List.copyOf(saldos);
    }
}
