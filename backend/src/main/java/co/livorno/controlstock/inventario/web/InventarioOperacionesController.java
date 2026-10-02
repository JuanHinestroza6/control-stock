package co.livorno.controlstock.inventario.web;

import co.livorno.controlstock.inventario.dto.AjusteRequest;
import co.livorno.controlstock.inventario.dto.EntradaRequest;
import co.livorno.controlstock.inventario.dto.MovimientoResponse;
import co.livorno.controlstock.inventario.servicio.OperacionInventarioService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioOperacionesController {

    private final OperacionInventarioService servicio;

    @PostMapping("/entradas")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MovimientoResponse> registrarEntrada(@Valid @RequestBody EntradaRequest req) {
        return servicio.registrarEntrada(req);
    }

    @PostMapping("/ajustes")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO')")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse registrarAjuste(@Valid @RequestBody AjusteRequest req) {
        return servicio.registrarAjuste(req);
    }
}
