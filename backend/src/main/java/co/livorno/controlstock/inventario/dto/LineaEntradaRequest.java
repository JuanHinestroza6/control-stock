package co.livorno.controlstock.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LineaEntradaRequest(
        @NotNull Long varianteId,
        @NotNull @Min(1) @Max(100000) Integer cantidad,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costoUnitario) {
}
