package co.livorno.controlstock.inventario.servicio;

import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.inventario.dto.InventarioItemResponse;
import co.livorno.controlstock.inventario.repositorio.InventarioRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del inventario actual (solo lectura). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    public static final Set<String> ORDEN_PERMITIDO = Set.of("sku", "producto.nombre", "stockFisico");

    private final InventarioRepository inventario;

    public Page<InventarioItemResponse> listar(String q, Long categoriaId, EstadoStock estadoStock,
            boolean incluirInactivas, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        return inventario.buscar(ProductoRepository.patronContiene(q), categoriaId, estadoStock, incluirInactivas,
                pageable).map(InventarioItemResponse::de);
    }
}
