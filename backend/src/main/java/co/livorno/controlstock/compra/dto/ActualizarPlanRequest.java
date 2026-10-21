package co.livorno.controlstock.compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Reemplaza observación y líneas del BORRADOR; {@code version} es la que el cliente leyó (409 si está vieja). */
public record ActualizarPlanRequest(
        @NotNull Long version,
        @Size(max = 255) String observacion,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull LineaPlanRequest> lineas) {
}
