package co.livorno.controlstock.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Venta anulada dentro del rango (por fecha de venta). numero es V-000123. */
public record VentaAnuladaItem(Long ventaId, String numero, LocalDateTime fecha, BigDecimal total,
        LocalDateTime anuladaEn, String anuladaPor, String motivoAnulacion) {}
