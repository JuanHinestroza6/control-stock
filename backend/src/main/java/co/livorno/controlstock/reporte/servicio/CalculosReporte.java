package co.livorno.controlstock.reporte.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Aritmética común de los reportes (ADR-011, R6): dinero con escala 2 y porcentajes con escala 1, HALF_UP. */
final class CalculosReporte {

    static final String MENSAJE_SIN_VENTAS = "No hay ventas en el periodo";

    private CalculosReporte() {}

    static BigDecimal dinero(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code parte / total × 100} con escala 1; null si el total es 0. */
    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return null;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    static BigDecimal suma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
