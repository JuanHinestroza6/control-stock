package co.livorno.controlstock.inventario.web;

import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.inventario.dto.InventarioItemResponse;
import co.livorno.controlstock.inventario.servicio.InventarioService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService servicio;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<InventarioItemResponse> listar(
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda no puede superar 60 caracteres") String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) EstadoStock estadoStock,
            @RequestParam(defaultValue = "false") boolean incluirInactivas,
            @PageableDefault(size = 20, sort = {"producto.nombre", "sku"}) Pageable pageable) {
        return servicio.listar(q, categoriaId, estadoStock, incluirInactivas, pageable);
    }
}
