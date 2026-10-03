package co.livorno.controlstock.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CrearReservaRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(min = 2, max = 120, message = "El nombre del cliente debe tener entre 2 y 120 caracteres")
        String clienteNombre,
        @NotBlank(message = "El contacto del cliente es obligatorio")
        @Size(min = 3, max = 120, message = "El contacto del cliente debe tener entre 3 y 120 caracteres")
        String clienteContacto,
        @Min(value = 1, message = "La vigencia mínima es 1 hora")
        @Max(value = 168, message = "La vigencia máxima es 168 horas") Integer vigenciaHoras,
        @NotEmpty(message = "Agrega al menos una línea")
        @Size(max = 20, message = "Una reserva admite hasta 20 líneas") List<@Valid LineaReservaRequest> lineas) {
}
