package co.livorno.controlstock.compra.servicio;

import co.livorno.controlstock.compra.repositorio.DetallePlanRepository;
import co.livorno.controlstock.compra.repositorio.EnCaminoVariante;
import co.livorno.controlstock.inventario.servicio.ConsultaPedidosEnCamino;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementa el puerto de inventario con las líneas de planes APROBADO (ADR-010). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidosEnCaminoJpa implements ConsultaPedidosEnCamino {

    private final DetallePlanRepository detalles;

    @Override
    public long enCamino(Long varianteId) {
        return detalles.enCamino(varianteId);
    }

    @Override
    public Map<Long, Long> enCaminoPorVariante() {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        for (EnCaminoVariante e : detalles.enCaminoPorVariante()) {
            mapa.put(e.varianteId(), e.unidades());
        }
        return mapa;
    }
}
