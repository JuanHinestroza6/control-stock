package co.livorno.controlstock.inventario.web;

import co.livorno.controlstock.inventario.dto.CancelarReservaRequest;
import co.livorno.controlstock.inventario.dto.CrearReservaRequest;
import co.livorno.controlstock.inventario.dto.ExtenderReservaRequest;
import co.livorno.controlstock.inventario.dto.ReservaResponse;
import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.servicio.ReservaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private static final String ROLES = "hasAnyRole('ADMIN','INVENTARIO','VENDEDOR')";

    private final ReservaService servicio;

    @GetMapping
    @PreAuthorize(ROLES)
    public Page<ReservaResponse> listar(@RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) String q, @RequestParam(required = false) Long varianteId,
            @RequestParam(defaultValue = "false") boolean porVencer,
            @PageableDefault(size = 20, sort = "venceEn", direction = Sort.Direction.ASC) Pageable pageable) {
        return servicio.listar(estado, q, varianteId, porVencer, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public ReservaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest request) {
        return servicio.crear(request);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize(ROLES)
    public ReservaResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarReservaRequest request) {
        return servicio.cancelar(id, request);
    }

    @PostMapping("/{id}/extender")
    @PreAuthorize(ROLES)
    public ReservaResponse extender(@PathVariable Long id, @Valid @RequestBody ExtenderReservaRequest request) {
        return servicio.extender(id, request);
    }
}
