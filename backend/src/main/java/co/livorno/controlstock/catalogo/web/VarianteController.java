package co.livorno.controlstock.catalogo.web;

import co.livorno.controlstock.catalogo.dto.GenerarVariantesRequest;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse;
import co.livorno.controlstock.catalogo.dto.VarianteResponse;
import co.livorno.controlstock.catalogo.servicio.VarianteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/productos/{productoId}/variantes")
@RequiredArgsConstructor
public class VarianteController {

    private final VarianteService servicio;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<VarianteResponse> listar(@PathVariable Long productoId,
            @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return servicio.listar(productoId, incluirInactivas);
    }

    @PostMapping("/generar")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerarVariantesResponse generar(@PathVariable Long productoId,
            @Valid @RequestBody GenerarVariantesRequest req) {
        return servicio.generar(productoId, req);
    }
}
