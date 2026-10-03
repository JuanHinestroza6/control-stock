package co.livorno.controlstock.venta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Convierte 1 a 20 reservas ACTIVAS del mismo cliente en una sola venta. */
public record ConvertirReservasRequest(
        @NotEmpty(message = "Indica al menos una reserva")
        @Size(max = 20, message = "Se pueden convertir máximo 20 reservas a la vez")
        List<@NotNull(message = "El id de la reserva es obligatorio") Long> reservaIds,
        List<@Valid PrecioReservaRequest> precios) {
}
