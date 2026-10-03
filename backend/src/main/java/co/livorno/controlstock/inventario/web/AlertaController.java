package co.livorno.controlstock.inventario.web;

import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.inventario.dto.AlertaResponse;
import co.livorno.controlstock.inventario.dto.ResumenAlertasResponse;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService servicio;
    private final UsuarioActual usuarioActual;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','VENDEDOR')")
    public Page<AlertaResponse> listar(@RequestParam(required = false) EstadoAlerta estado,
            @RequestParam(required = false) TipoAlerta tipo,
            @PageableDefault(size = 20, sort = "creadaEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return servicio.listar(estado, tipo, pageable);
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','VENDEDOR')")
    public ResumenAlertasResponse resumen() {
        return servicio.resumen();
    }

    @PostMapping("/{id}/atender")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    public AlertaResponse atender(@PathVariable Long id) {
        return servicio.atender(id, usuarioActual.id());
    }
}
