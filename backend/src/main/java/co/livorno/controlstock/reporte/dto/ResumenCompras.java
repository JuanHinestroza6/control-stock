package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Plan de compras: borradores, aprobados en camino, unidades en camino y planes atrasados (llegada esperada vencida).
 * valorEnCamino (costo estimado) solo llega a ADMIN y CONTABILIDAD (null para INVENTARIO).
 */
public record ResumenCompras(long planesBorrador, long planesEnCamino, long unidadesEnCamino, long planesAtrasados,
        BigDecimal valorEnCamino) {}
