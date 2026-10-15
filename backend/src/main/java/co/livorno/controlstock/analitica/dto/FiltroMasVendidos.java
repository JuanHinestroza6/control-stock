package co.livorno.controlstock.analitica.dto;

import java.time.LocalDate;

/** Filtro ya validado del ranking: rango inclusivo {@code [desde, hasta]}, categoría opcional, top-N. */
public record FiltroMasVendidos(LocalDate desde, LocalDate hasta, Long categoriaId, CriterioRanking criterio,
        AgrupacionRanking agrupacion, int limite) {}
