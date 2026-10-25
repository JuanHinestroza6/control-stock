package co.livorno.controlstock.reporte.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Valorización del inventario al momento generadoEn (REQ-07). mensaje es null si hay existencias. */
public record ValorizacionResponse(Long categoriaId, String categoria, AgrupacionReporte agrupacion,
        boolean incluirInactivas, boolean sinExistencias, String mensaje, List<FilaValorizacion> filas,
        FilaValorizacion totales, List<String> notas, LocalDateTime generadoEn) {}
