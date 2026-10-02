package co.livorno.controlstock.venta.web;

import co.livorno.controlstock.venta.dto.AnularVentaRequest;
import co.livorno.controlstock.venta.dto.DevolucionRequest;
import co.livorno.controlstock.venta.dto.DevolucionResponse;
import co.livorno.controlstock.venta.dto.RegistrarVentaRequest;
import co.livorno.controlstock.venta.dto.VentaDetalleResponse;
import co.livorno.controlstock.venta.dto.VentaResumenResponse;
import co.livorno.controlstock.venta.modelo.EstadoVenta;
import co.livorno.controlstock.venta.servicio.DevolucionService;
import co.livorno.controlstock.venta.servicio.VentaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService servicio;
    private final DevolucionService devolucionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaDetalleResponse registrar(@Valid @RequestBody RegistrarVentaRequest request) {
        return servicio.registrar(request);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN')")
    public VentaDetalleResponse anular(@PathVariable Long id, @Valid @RequestBody AnularVentaRequest request) {
        return servicio.anular(id, request);
    }

    @PostMapping("/{id}/devoluciones")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public DevolucionResponse devolver(@PathVariable Long id, @Valid @RequestBody DevolucionRequest request) {
        return devolucionService.registrar(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR','CONTABILIDAD')")
    public Page<VentaResumenResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoVenta estado,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return servicio.listar(desde, hasta, estado, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR','CONTABILIDAD')")
    public VentaDetalleResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
