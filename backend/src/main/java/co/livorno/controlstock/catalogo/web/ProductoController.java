package co.livorno.controlstock.catalogo.web;

import co.livorno.controlstock.catalogo.dto.ActualizarProductoRequest;
import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.dto.ProductoResumenResponse;
import co.livorno.controlstock.catalogo.servicio.ProductoService;
import co.livorno.controlstock.comun.dto.EstadoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService servicio;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ProductoResumenResponse> listar(
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda no puede superar 60 caracteres") String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        return servicio.listar(q, categoriaId, activo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProductoDetalleResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoDetalleResponse crear(@Valid @RequestBody CrearProductoRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public ProductoDetalleResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarProductoRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public ProductoDetalleResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest req) {
        return servicio.cambiarEstado(id, req.activo());
    }
}
