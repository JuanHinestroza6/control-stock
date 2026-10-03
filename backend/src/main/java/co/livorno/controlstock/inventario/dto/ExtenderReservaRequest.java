package co.livorno.controlstock.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtenderReservaRequest(
        @NotNull(message = "Las horas son obligatorias") @Min(value = 1, message = "El mínimo es 1 hora")
        @Max(value = 168, message = "El máximo es 168 horas") Integer horas) {
}
