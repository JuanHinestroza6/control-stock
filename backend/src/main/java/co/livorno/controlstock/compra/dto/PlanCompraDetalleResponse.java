package co.livorno.controlstock.compra.dto;

import co.livorno.controlstock.compra.modelo.DetallePlan;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.OrigenPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Detalle del plan con sus líneas. {@code omitidas} (SKUs ya en otro borrador) y {@code excluidasPorTope}
 * (candidatas que no entraron por el tope de 300 líneas) solo se llenan al sugerir; en los demás casos, null y 0. */
public record PlanCompraDetalleResponse(
        Long id,
        String numero,
        EstadoPlan estado,
        OrigenPlan origen,
        LocalDateTime creadoEn,
        String creadoPor,
        LocalDateTime aprobadoEn,
        String aprobadoPor,
        LocalDateTime recibidoEn,
        int unidades,
        BigDecimal costoEstimadoTotal,
        LocalDateTime llegadaEsperada,
        boolean atrasado,
        long version,
        String observacion,
        String motivoCancelacion,
        LocalDateTime canceladoEn,
        String canceladoPor,
        List<String> omitidas,
        int excluidasPorTope,
        List<LineaPlanResponse> lineas) {

    public static PlanCompraDetalleResponse de(PlanCompra p, Map<Long, String> nombres, Map<Long, Long> enCamino,
            LocalDateTime ahora, List<String> omitidas, int excluidasPorTope) {
        LocalDateTime llegada = llegadaEsperada(p);
        List<LineaPlanResponse> lineas = p.getLineas().stream()
                .sorted(Comparator.comparing((DetallePlan d) -> d.getVariante().getId()))
                .map(d -> LineaPlanResponse.de(d, enCamino.getOrDefault(d.getVariante().getId(), 0L)))
                .toList();
        return new PlanCompraDetalleResponse(p.getId(), p.numero(), p.getEstado(), p.getOrigen(), p.getCreadoEn(),
                nombres.get(p.getCreadoPor()), p.getAprobadoEn(), nombres.get(p.getAprobadoPor()), p.getRecibidoEn(),
                unidades(p), costoTotal(p), llegada, atrasado(p, llegada, ahora), p.getVersion(), p.getObservacion(),
                p.getMotivoCancelacion(), p.getCanceladoEn(), nombres.get(p.getCanceladoPor()), omitidas, excluidasPorTope, lineas);
    }

    static int unidades(PlanCompra p) {
        return p.getLineas().stream().mapToInt(DetallePlan::getCantidadAprobada).sum();
    }

    static BigDecimal costoTotal(PlanCompra p) {
        return p.getLineas().stream()
                .map(d -> d.getCostoEstimado().multiply(BigDecimal.valueOf(d.getCantidadAprobada())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code aprobadoEn + max(leadTimeDias)} de los productos del plan; null si no está aprobado. */
    static LocalDateTime llegadaEsperada(PlanCompra p) {
        if (p.getEstado() != EstadoPlan.APROBADO || p.getAprobadoEn() == null) {
            return null;
        }
        int lead = p.getLineas().stream().mapToInt(d -> d.getVariante().getProducto().getLeadTimeDias()).max()
                .orElse(0);
        return p.getAprobadoEn().plusDays(lead);
    }

    static boolean atrasado(PlanCompra p, LocalDateTime llegada, LocalDateTime ahora) {
        return p.getEstado() == EstadoPlan.APROBADO && llegada != null && llegada.isBefore(ahora);
    }
}
