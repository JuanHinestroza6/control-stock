package co.livorno.controlstock.compra.web;

import co.livorno.controlstock.compra.dto.ActualizarPlanRequest;
import co.livorno.controlstock.compra.dto.AprobarPlanRequest;
import co.livorno.controlstock.compra.dto.CancelarPlanRequest;
import co.livorno.controlstock.compra.dto.CrearPlanRequest;
import co.livorno.controlstock.compra.dto.PlanCompraDetalleResponse;
import co.livorno.controlstock.compra.dto.PlanCompraResumenResponse;
import co.livorno.controlstock.compra.dto.RecibirPlanRequest;
import co.livorno.controlstock.compra.dto.SugerirPlanRequest;
import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.servicio.PlanCompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Planes de compra (ADR-010). Sin lógica de negocio: delega en {@link PlanCompraService}. */
@RestController
@RequestMapping("/api/compras/planes")
@RequiredArgsConstructor
public class PlanCompraController {

    private final PlanCompraService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public Page<PlanCompraResumenResponse> listar(@RequestParam(required = false) EstadoPlan estado,
            @PageableDefault(size = 20, sort = "creadoEn", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return service.listar(estado, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public PlanCompraDetalleResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCompraDetalleResponse crear(@Valid @RequestBody CrearPlanRequest request) {
        return service.crear(request);
    }

    @PostMapping("/sugerido")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCompraDetalleResponse sugerir(@Valid @RequestBody SugerirPlanRequest request) {
        return service.crearSugerido(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public PlanCompraDetalleResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarPlanRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanCompraDetalleResponse aprobar(@PathVariable Long id, @Valid @RequestBody AprobarPlanRequest request) {
        return service.aprobar(id, request);
    }

    /** El servicio limita a INVENTARIO a los borradores (403 sobre un plan aprobado). */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public PlanCompraDetalleResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarPlanRequest request) {
        return service.cancelar(id, request);
    }

    @PostMapping("/{id}/recibir")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public PlanCompraDetalleResponse recibir(@PathVariable Long id, @Valid @RequestBody RecibirPlanRequest request) {
        return service.recibir(id, request);
    }
}
