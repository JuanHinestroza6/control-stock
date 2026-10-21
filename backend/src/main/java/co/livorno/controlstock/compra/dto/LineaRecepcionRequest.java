package co.livorno.controlstock.compra.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record LineaRecepcionRequest(
        @NotNull Long detalleId,
        @NotNull @Min(0) @Max(100000) Integer cantidadRecibida,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costoUnitario) {
}
