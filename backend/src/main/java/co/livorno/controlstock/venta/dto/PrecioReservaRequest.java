package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Precio unitario de una variante al convertir (opcional; no puede superar el precio de lista). */
public record PrecioReservaRequest(
        @NotNull(message = "La variante es obligatoria") Long varianteId,
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", inclusive = false, message = "El precio debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales") BigDecimal precioUnitario) {
}
