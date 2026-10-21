package co.livorno.controlstock.compra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarPlanRequest(@NotBlank @Size(max = 200) String motivo) {
}
