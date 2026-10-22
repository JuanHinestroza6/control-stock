package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila de valorización: físico por precioCompra actual (incluye lo reservado). codigo es el código del producto o el
 * SKU (null al agrupar por categoría); categoria es null al agrupar por categoría y en la fila de totales.
 * unidadesEnCamino y valorEnCamino son informativos: no suman al valor del inventario. margenPotencialPorcentaje
 * (escala 1, sobre el valor a precio de venta) es null si ese valor es 0.
 */
public record FilaValorizacion(Long id, String codigo, String nombre, String categoria, long unidadesFisicas,
        long unidadesReservadas, BigDecimal valorCosto, BigDecimal valorVenta, BigDecimal margenPotencial,
        BigDecimal margenPotencialPorcentaje, long unidadesEnCamino, BigDecimal valorEnCamino) {}
