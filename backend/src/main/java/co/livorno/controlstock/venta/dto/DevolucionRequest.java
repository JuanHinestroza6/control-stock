package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DevolucionRequest(
        @NotNull(message = "La línea de venta es obligatoria") Long detalleVentaId,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
        Integer cantidad,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        @Pattern(regexp = "^\\s*\\S[\\s\\S]{3,}\\S\\s*$",
                message = "El motivo debe tener al menos 5 caracteres sin contar espacios en los extremos")
        String motivo,
        @NotNull(message = "Indica si la prenda está apta para la venta") Boolean aptaParaVenta) {
}
