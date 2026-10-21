package co.livorno.controlstock.compra.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Línea de un plan: variante, cantidad (1 a 100.000) y costo estimado opcional (por defecto el precioCompra). */
public record LineaPlanRequest(
        @NotNull Long varianteId,
        @NotNull @Min(1) @Max(100_000) Integer cantidad,
        @DecimalMin("0.00") BigDecimal costoEstimado) {
}
