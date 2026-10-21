package co.livorno.controlstock.analitica.dto;

import java.math.BigDecimal;

/** Parámetros de suavizado del método elegido (beta y gamma solo en Holt-Winters). */
public record ParametrosModelo(BigDecimal alfa, BigDecimal beta, BigDecimal gamma) {}
