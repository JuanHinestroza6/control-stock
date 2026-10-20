package co.livorno.controlstock.inventario.servicio;

import java.util.Map;

/**
 * Puerto que inventario usa para conocer las unidades en camino (ADR-010): lo implementa compra, así inventario no
 * depende de compra. En camino = Σ cantidadAprobada de las líneas de planes APROBADO.
 */
public interface ConsultaPedidosEnCamino {

    /** Unidades en camino de una variante (0 si no hay). */
    long enCamino(Long varianteId);

    /** Unidades en camino por variante; solo aparecen las que tienen pedido aprobado. */
    Map<Long, Long> enCaminoPorVariante();
}
