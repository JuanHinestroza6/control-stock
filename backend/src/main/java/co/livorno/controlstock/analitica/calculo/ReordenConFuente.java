package co.livorno.controlstock.analitica.calculo;

import co.livorno.controlstock.analitica.modelo.FuenteDemanda;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Punto de reorden con la fuente de su demanda del lead time (ADR-010, decisión 3): columnas {@code fuente_demanda},
 * {@code demanda_lead_time} y {@code tasa_pronosticada} de {@code analitica_variante}.
 *
 * @param reorden          resultado de siempre; {@code demandaMedia} y {@code desviacion} son los de la serie diaria
 *                         observada de 90 días (s es el mismo con ambas fuentes)
 * @param fuente           PRONOSTICO si el producto tiene pronóstico; HISTORICA si no
 * @param demandaLeadTime  μL usada, escala 2 HALF_UP: Σ tasa pronosticada de L días (PRONOSTICO) o d̄ × L
 *                         (HISTORICA); null con HISTORICA y SIN_HISTORIA. Con PRONOSTICO y SIN_HISTORIA se informa
 *                         aunque el ROP sea null
 * @param tasaPronosticada tasa diaria media del lead time, μL / L (L = 0 → tasa del día del corte), escala 4 HALF_UP;
 *                         null con HISTORICA
 */
public record ReordenConFuente(ResultadoReorden reorden, FuenteDemanda fuente, BigDecimal demandaLeadTime,
        BigDecimal tasaPronosticada) {

    public ReordenConFuente {
        Objects.requireNonNull(reorden, "reorden");
        Objects.requireNonNull(fuente, "fuente");
        if ((fuente == FuenteDemanda.PRONOSTICO) != (tasaPronosticada != null)) {
            throw new IllegalArgumentException("La tasa pronosticada existe solo con la fuente PRONOSTICO.");
        }
    }
}
