package co.livorno.controlstock.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CrearProductoRequest(
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9-]{2,20}", message = "El código debe tener de 2 a 20 letras, números o guiones")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,
        @NotNull(message = "La categoría es obligatoria") Long categoriaId,
        @NotNull(message = "El precio de compra es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio de compra no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio de compra admite hasta 10 enteros y 2 decimales")
        BigDecimal precioCompra,
        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.00", inclusive = false, message = "El precio de venta debe ser mayor a 0")
        @Digits(integer = 10, fraction = 2, message = "El precio de venta admite hasta 10 enteros y 2 decimales")
        BigDecimal precioVenta,
        @Min(value = 1, message = "El tiempo de reposición mínimo es 1 día")
        @Max(value = 365, message = "El tiempo de reposición máximo es 365 días")
        Integer leadTimeDias,
        @Min(value = 1, message = "El lote mínimo es 1")
        @Max(value = 10000, message = "El lote máximo es 10000")
        Integer loteMinimo) {
}
