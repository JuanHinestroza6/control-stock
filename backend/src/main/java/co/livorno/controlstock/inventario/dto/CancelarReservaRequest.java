package co.livorno.controlstock.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CancelarReservaRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 200, message = "El motivo no puede superar 200 caracteres")
        @Pattern(regexp = "^\\s*\\S[\\s\\S]{3,}\\S\\s*$",
                message = "El motivo debe tener al menos 5 caracteres sin contar espacios en los extremos")
        String motivo) {
}
