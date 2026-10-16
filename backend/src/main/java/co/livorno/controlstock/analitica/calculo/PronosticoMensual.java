package co.livorno.controlstock.analitica.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Pronóstico mensual de una variante (o de un producto) convertido en tasa diaria (ADR-010, decisión 3).
 *
 * <pre>
 * tasa(día) = pronóstico(mes del día) / días de ese mes
 * más allá del horizonte: la tasa del último mes pronosticado (su cantidad / sus días)
 * antes del primer mes:   la tasa del primer mes (no ocurre si el corte cae en el mes actual)
 * demanda(desde, d) = Σ_{i=0..d−1} tasa(desde + i)
 * </pre>
 *
 * Ejemplo a mano: octubre 31 (1/día) y noviembre 60 (2/día); desde el 25-oct, 15 días → 7 × 1 + 8 × 2 = 23.
 *
 * @param primerMes  mes del primer pronóstico (el mes en curso, R4 de ADR-010)
 * @param cantidades pronóstico de cada mes desde {@code primerMes}, no negativo, al menos uno
 */
public record PronosticoMensual(YearMonth primerMes, List<BigDecimal> cantidades) {

    public PronosticoMensual {
        Objects.requireNonNull(primerMes, "primerMes");
        Objects.requireNonNull(cantidades, "cantidades");
        cantidades = List.copyOf(cantidades);
        if (cantidades.isEmpty()) {
            throw new IllegalArgumentException("El pronóstico debe tener al menos un mes.");
        }
        for (BigDecimal cantidad : cantidades) {
            if (cantidad.signum() < 0) {
                throw new IllegalArgumentException("El pronóstico no puede ser negativo: " + cantidad);
            }
        }
    }

    /** Tasa diaria pronosticada para {@code dia}. */
    public double tasaDiaria(LocalDate dia) {
        Objects.requireNonNull(dia, "dia");
        long indice = primerMes.until(YearMonth.from(dia), ChronoUnit.MONTHS);
        int i = (int) Math.max(0, Math.min(cantidades.size() - 1, indice));
        YearMonth mes = primerMes.plusMonths(i);
        return cantidades.get(i).doubleValue() / mes.lengthOfMonth();
    }

    /**
     * Demanda pronosticada en {@code [desde, desde + dias)}: suma de la tasa diaria de cada día (cruza meses de distinto
     * número de días; agotado el horizonte sigue con la tasa del último mes).
     *
     * @throws IllegalArgumentException si {@code dias} es negativo
     */
    public double demanda(LocalDate desde, int dias) {
        Objects.requireNonNull(desde, "desde");
        if (dias < 0) {
            throw new IllegalArgumentException("El número de días no puede ser negativo: " + dias);
        }
        double suma = 0.0;
        LocalDate dia = desde;
        int restantes = dias;
        while (restantes > 0) {
            // Bloque de días del mismo mes: misma tasa (menos sumas y menos error de redondeo).
            LocalDate finMes = YearMonth.from(dia).atEndOfMonth();
            int enEsteMes = (int) Math.min(restantes, ChronoUnit.DAYS.between(dia, finMes) + 1);
            suma += enEsteMes * tasaDiaria(dia);
            restantes -= enEsteMes;
            dia = dia.plusDays(enEsteMes);
        }
        return suma;
    }
}
