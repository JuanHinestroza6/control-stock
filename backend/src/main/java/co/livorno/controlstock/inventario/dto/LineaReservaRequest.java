package co.livorno.controlstock.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LineaReservaRequest(
        @NotNull(message = "La variante es obligatoria") Long varianteId,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 100, message = "La cantidad máxima es 100") Integer cantidad) {
}
