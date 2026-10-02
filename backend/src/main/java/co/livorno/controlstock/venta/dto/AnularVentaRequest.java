package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularVentaRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 255, message = "El motivo debe tener entre 5 y 255 caracteres") String motivo) {
}
