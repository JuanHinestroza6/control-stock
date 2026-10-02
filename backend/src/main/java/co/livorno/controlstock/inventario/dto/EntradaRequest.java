package co.livorno.controlstock.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EntradaRequest(
        @Size(max = 255) String observacion,
        @NotEmpty @Size(max = 100) List<@Valid LineaEntradaRequest> lineas) {
}
