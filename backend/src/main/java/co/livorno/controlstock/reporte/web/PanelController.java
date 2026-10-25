package co.livorno.controlstock.reporte.web;

import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.reporte.dto.PanelResponse;
import co.livorno.controlstock.reporte.servicio.PanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel de inicio por rol (ADR-011): las secciones no permitidas para el rol del usuario llegan en null. */
@RestController
@RequestMapping("/api/panel")
@RequiredArgsConstructor
public class PanelController {

    private final PanelService servicio;
    private final UsuarioActual usuarioActual;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PanelResponse panel() {
        return servicio.obtener(usuarioActual.obtener().rol());
    }
}
