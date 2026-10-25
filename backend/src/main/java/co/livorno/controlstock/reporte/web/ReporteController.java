package co.livorno.controlstock.reporte.web;

import co.livorno.controlstock.comun.exportacion.FormatoExportacion;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.RespuestaArchivo;
import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.CriterioRentabilidad;
import co.livorno.controlstock.reporte.dto.FiltroLibroVentas;
import co.livorno.controlstock.reporte.dto.FiltroMovimientos;
import co.livorno.controlstock.reporte.dto.FiltroRentabilidad;
import co.livorno.controlstock.reporte.dto.FiltroValorizacion;
import co.livorno.controlstock.reporte.dto.LibroVentasResponse;
import co.livorno.controlstock.reporte.dto.MovimientosResponse;
import co.livorno.controlstock.reporte.dto.PeriodoLibro;
import co.livorno.controlstock.reporte.dto.RentabilidadResponse;
import co.livorno.controlstock.reporte.dto.ValorizacionResponse;
import co.livorno.controlstock.reporte.servicio.LibroVentasService;
import co.livorno.controlstock.reporte.servicio.MovimientosPeriodoService;
import co.livorno.controlstock.reporte.servicio.RentabilidadService;
import co.livorno.controlstock.reporte.servicio.ValorizacionService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes contables y de rentabilidad (REQ-03, REQ-07, ADR-011): ADMIN y CONTABILIDAD. Sin lógica de negocio. */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final RentabilidadService rentabilidadService;
    private final LibroVentasService libroVentasService;
    private final ValorizacionService valorizacionService;
    private final MovimientosPeriodoService movimientosService;
    private final UsuarioActual usuarioActual;

    @GetMapping("/rentabilidad")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public RentabilidadResponse rentabilidad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion,
            @RequestParam(required = false) CriterioRentabilidad orden) {
        return rentabilidadService.consultar(new FiltroRentabilidad(desde, hasta, categoriaId, agrupacion, orden));
    }

    @GetMapping("/rentabilidad/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<byte[]> exportarRentabilidad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion,
            @RequestParam(required = false) CriterioRentabilidad orden,
            @RequestParam FormatoExportacion formato) {
        return RespuestaArchivo.de(rentabilidadService.exportar(
                new FiltroRentabilidad(desde, hasta, categoriaId, agrupacion, orden), formato, generadoPor()));
    }

    @GetMapping("/libro-ventas")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public LibroVentasResponse libroVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) PeriodoLibro agrupacion) {
        return libroVentasService.consultar(new FiltroLibroVentas(desde, hasta, agrupacion));
    }

    @GetMapping("/libro-ventas/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<byte[]> exportarLibroVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) PeriodoLibro agrupacion,
            @RequestParam FormatoExportacion formato) {
        return RespuestaArchivo.de(libroVentasService.exportar(new FiltroLibroVentas(desde, hasta, agrupacion),
                formato, generadoPor()));
    }

    @GetMapping("/valorizacion")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ValorizacionResponse valorizacion(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion,
            @RequestParam(required = false) Boolean incluirInactivas) {
        return valorizacionService.consultar(filtroValorizacion(categoriaId, agrupacion, incluirInactivas));
    }

    @GetMapping("/valorizacion/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<byte[]> exportarValorizacion(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion,
            @RequestParam(required = false) Boolean incluirInactivas,
            @RequestParam FormatoExportacion formato) {
        return RespuestaArchivo.de(valorizacionService.exportar(
                filtroValorizacion(categoriaId, agrupacion, incluirInactivas), formato, generadoPor()));
    }

    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public MovimientosResponse movimientos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion) {
        return movimientosService.consultar(new FiltroMovimientos(desde, hasta, categoriaId, agrupacion));
    }

    @GetMapping("/movimientos/exportar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public ResponseEntity<byte[]> exportarMovimientos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) AgrupacionReporte agrupacion,
            @RequestParam FormatoExportacion formato) {
        return RespuestaArchivo.de(movimientosService.exportar(
                new FiltroMovimientos(desde, hasta, categoriaId, agrupacion), formato, generadoPor()));
    }

    /** Por defecto la valorización incluye las inactivas con stock. */
    private static FiltroValorizacion filtroValorizacion(Long categoriaId, AgrupacionReporte agrupacion,
            Boolean incluirInactivas) {
        return new FiltroValorizacion(categoriaId, agrupacion, incluirInactivas == null || incluirInactivas);
    }

    private String generadoPor() {
        return usuarioActual.obtener().nombre();
    }
}
