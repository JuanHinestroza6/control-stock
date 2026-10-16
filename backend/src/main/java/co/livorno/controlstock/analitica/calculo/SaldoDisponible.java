package co.livorno.controlstock.analitica.calculo;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Disponible de una variante (físico − reservado) inmediatamente después de un movimiento del kardex, con el día del
 * movimiento. Es la entrada de {@link CorrectorCensura}: la consulta los entrega en orden (fecha, id).
 */
public record SaldoDisponible(LocalDate dia, int disponible) {

    public SaldoDisponible {
        Objects.requireNonNull(dia, "dia");
        if (disponible < 0) {
            throw new IllegalArgumentException("El disponible no puede ser negativo: " + disponible);
        }
    }
}
