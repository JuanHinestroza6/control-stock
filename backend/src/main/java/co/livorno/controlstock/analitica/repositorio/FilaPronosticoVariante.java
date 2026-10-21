package co.livorno.controlstock.analitica.repositorio;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Cantidad proyectada de una variante en un mes, con sus datos de identificación. */
public record FilaPronosticoVariante(long varianteId, String sku, String talla, String color, YearMonth periodo,
        BigDecimal cantidad) {}
