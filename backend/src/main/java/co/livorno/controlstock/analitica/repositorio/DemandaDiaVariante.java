package co.livorno.controlstock.analitica.repositorio;

import java.time.LocalDate;

/**
 * Fila dispersa de demanda de inventario: neto de un día de una variante (vendidas − devueltas aptas). Puede ser
 * negativo; el recorte a 0 y el relleno de ceros los hace {@code SerieDemanda}.
 */
public record DemandaDiaVariante(long varianteId, LocalDate dia, int neto) {
}
