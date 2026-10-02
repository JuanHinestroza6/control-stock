package co.livorno.controlstock.venta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegistrarVentaRequest(
        @NotEmpty(message = "La venta requiere al menos una línea")
        @Size(max = 50, message = "Una venta admite máximo 50 líneas") List<@Valid LineaVentaRequest> lineas) {
}
