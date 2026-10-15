package co.livorno.controlstock.catalogo.repositorio;

import java.time.LocalDateTime;

/** Insumos de lectura de una variante activa de un producto activo para la analítica (ADR-009). */
public record VarianteAnalizable(Long id, int leadTimeDias, LocalDateTime creadoEn) {
}
