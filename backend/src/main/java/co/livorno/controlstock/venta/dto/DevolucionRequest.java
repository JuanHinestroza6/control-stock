package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DevolucionRequest(
        @NotNull(message = "La línea de venta es obligatoria") Long detalleVentaId,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
        Integer cantidad,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 255, message = "El motivo debe tener entre 5 y 255 caracteres") String motivo,
        @NotNull(message = "Indica si la prenda está apta para la venta") Boolean aptaParaVenta) {
}
