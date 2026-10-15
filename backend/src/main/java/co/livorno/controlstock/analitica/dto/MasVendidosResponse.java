package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ranking de más vendidos con los totales netos del periodo (de todo el periodo y categoría, no solo del top). */
public record MasVendidosResponse(LocalDate desde, LocalDate hasta, CriterioRanking criterio,
        AgrupacionRanking agrupacion, Long categoriaId, long totalUnidades, BigDecimal totalIngreso,
        List<MasVendidoItem> items) {}
