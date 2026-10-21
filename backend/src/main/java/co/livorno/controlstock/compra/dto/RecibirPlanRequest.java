package co.livorno.controlstock.compra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecibirPlanRequest(
        @Size(max = 255) String observacion,
        @NotEmpty @Valid List<LineaRecepcionRequest> lineas) {
}
