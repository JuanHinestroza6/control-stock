package co.livorno.controlstock.catalogo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActualizarVarianteRequest(
        @NotNull(message = "El stock mínimo es obligatorio")
        @Min(value = 0, message = "El stock mínimo no puede ser negativo")
        @Max(value = 100000, message = "El stock mínimo no puede superar 100000")
        Integer stockMinimo) {
}
