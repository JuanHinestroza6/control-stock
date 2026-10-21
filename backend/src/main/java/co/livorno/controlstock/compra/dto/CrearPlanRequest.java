package co.livorno.controlstock.compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearPlanRequest(
        @Size(max = 255) String observacion,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull LineaPlanRequest> lineas) {
}
