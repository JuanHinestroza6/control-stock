package co.livorno.controlstock.catalogo.dto;

import co.livorno.controlstock.catalogo.modelo.Talla;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerarVariantesRequest(
        @NotEmpty(message = "Selecciona al menos una talla")
        @Size(max = 7, message = "Máximo 7 tallas")
        List<@NotNull(message = "La talla es obligatoria") Talla> tallas,
        @NotEmpty(message = "Indica al menos un color")
        @Size(max = 20, message = "Máximo 20 colores")
        List<@NotBlank(message = "El color no puede estar vacío")
             @Size(max = 30, message = "El color no puede superar 30 caracteres")
             @Pattern(regexp = "^[\\p{L}\\p{N} ]*$", message = "El color solo admite letras, números y espacios")
             String> colores,
        @NotNull(message = "El stock mínimo es obligatorio")
        @Min(value = 0, message = "El stock mínimo no puede ser negativo")
        @Max(value = 100000, message = "El stock mínimo no puede superar 100000")
        Integer stockMinimo) {
}
