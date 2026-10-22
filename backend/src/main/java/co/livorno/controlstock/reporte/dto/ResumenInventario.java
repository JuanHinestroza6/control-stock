package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/** Inventario actual. valorCosto y valorVenta solo llegan a ADMIN y CONTABILIDAD (null para los demás). */
public record ResumenInventario(long variantesActivas, long unidadesFisicas, long unidadesDisponibles,
        long alertasAgotado, long alertasStockBajo, long alertasSinRotacion, BigDecimal valorCosto,
        BigDecimal valorVenta) {}
