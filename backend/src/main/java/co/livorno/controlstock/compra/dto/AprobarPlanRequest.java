package co.livorno.controlstock.compra.dto;

import jakarta.validation.constraints.NotNull;

public record AprobarPlanRequest(@NotNull Long version) {
}
