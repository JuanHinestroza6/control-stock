package co.livorno.controlstock.comun.dto;

import jakarta.validation.constraints.NotNull;

/** Cambio de estado activo/inactivo (borrado lógico). */
public record EstadoRequest(@NotNull(message = "El estado es obligatorio") Boolean activo) {}
