package co.livorno.controlstock.venta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LineaVentaRequest(
        @NotNull(message = "La variante es obligatoria") Long varianteId,
        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 10000, message = "La cantidad máxima es 10000") Integer cantidad,
        @DecimalMin(value = "0.00", inclusive = false, message = "El precio debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales") BigDecimal precioUnitario) {
}
