package co.livorno.controlstock.inventario.dto;

import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code tipo} solo admite AJUSTE_POSITIVO o AJUSTE_NEGATIVO (otro valor responde 400). */
public record AjusteRequest(
        @NotNull Long varianteId,
        @NotNull TipoMovimiento tipo,
        @NotNull @Min(1) @Max(100000) Integer cantidad,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^\\s*\\S[\\s\\S]{3,}\\S\\s*$",
                message = "La observación debe tener al menos 5 caracteres sin contar espacios en los extremos")
        String observacion) {
}
