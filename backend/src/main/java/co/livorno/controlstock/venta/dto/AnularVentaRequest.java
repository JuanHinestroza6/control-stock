package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnularVentaRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        @Pattern(regexp = "^\\s*\\S[\\s\\S]{3,}\\S\\s*$",
                message = "El motivo debe tener al menos 5 caracteres sin contar espacios en los extremos")
        String motivo) {
}
