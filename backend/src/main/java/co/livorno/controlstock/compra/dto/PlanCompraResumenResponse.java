package co.livorno.controlstock.compra.dto;

import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.OrigenPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** Fila del listado de planes. {@code lineas} es el conteo. */
public record PlanCompraResumenResponse(
        Long id,
        String numero,
        EstadoPlan estado,
        OrigenPlan origen,
        LocalDateTime creadoEn,
        String creadoPor,
        LocalDateTime aprobadoEn,
        String aprobadoPor,
        LocalDateTime recibidoEn,
        int lineas,
        int unidades,
        BigDecimal costoEstimadoTotal,
        LocalDateTime llegadaEsperada,
        boolean atrasado) {

    public static PlanCompraResumenResponse de(PlanCompra p, Map<Long, String> nombres, LocalDateTime ahora) {
        LocalDateTime llegada = PlanCompraDetalleResponse.llegadaEsperada(p);
        return new PlanCompraResumenResponse(p.getId(), p.numero(), p.getEstado(), p.getOrigen(), p.getCreadoEn(),
                nombres.get(p.getCreadoPor()), p.getAprobadoEn(), nombres.get(p.getAprobadoPor()), p.getRecibidoEn(),
                p.getLineas().size(), PlanCompraDetalleResponse.unidades(p), PlanCompraDetalleResponse.costoTotal(p),
                llegada, PlanCompraDetalleResponse.atrasado(p, llegada, ahora));
    }
}
