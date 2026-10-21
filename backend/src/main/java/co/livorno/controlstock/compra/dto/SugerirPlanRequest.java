package co.livorno.controlstock.compra.dto;

import jakarta.validation.constraints.Size;

/** Pide un plan sugerido; {@code categoriaId} null = todas las categorías. */
public record SugerirPlanRequest(Long categoriaId, @Size(max = 255) String observacion) {
}
