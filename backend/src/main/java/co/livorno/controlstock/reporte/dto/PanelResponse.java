package co.livorno.controlstock.reporte.dto;

import java.time.LocalDateTime;

/**
 * Panel de inicio por rol (ADR-011). Las secciones no permitidas llegan null: ventas (ADMIN, VENDEDOR, CONTABILIDAD),
 * inventario (todos), reservas (ADMIN, INVENTARIO, VENDEDOR), compras (ADMIN, INVENTARIO, CONTABILIDAD).
 */
public record PanelResponse(LocalDateTime generadoEn, ResumenVentas ventas, ResumenInventario inventario,
        ResumenReservas reservas, ResumenCompras compras) {}
