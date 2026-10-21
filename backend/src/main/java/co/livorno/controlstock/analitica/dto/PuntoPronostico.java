package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;

/** Cantidad proyectada de un mes ({@code periodo} = {@code YYYY-MM}). */
public record PuntoPronostico(String periodo, BigDecimal cantidad) {}
