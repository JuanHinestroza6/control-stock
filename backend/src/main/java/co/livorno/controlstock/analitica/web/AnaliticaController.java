package co.livorno.controlstock.analitica.web;

import co.livorno.controlstock.analitica.dto.AbcVarianteResponse;
import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.EjecucionAnaliticaResponse;
import co.livorno.controlstock.analitica.dto.MasVendidosResponse;
import co.livorno.controlstock.analitica.dto.PronosticoDetalleResponse;
import co.livorno.controlstock.analitica.dto.PronosticoProductoResponse;
import co.livorno.controlstock.analitica.dto.TendenciaResponse;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.servicio.ConsultaPronosticoService;
import co.livorno.controlstock.analitica.servicio.TendenciasService;
import co.livorno.controlstock.analitica.dto.ReordenVarianteResponse;
import co.livorno.controlstock.analitica.dto.ResumenAbcResponse;
import co.livorno.controlstock.analitica.modelo.ClaseAbc;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.servicio.ConsultaAnaliticaService;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import co.livorno.controlstock.analitica.servicio.RecalculoAnaliticoService;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analitica")
@RequiredArgsConstructor
public class AnaliticaController {

    private final MasVendidosService masVendidosService;
    private final ConsultaAnaliticaService consultaService;
    private final RecalculoAnaliticoService recalculoService;
    private final TendenciasService tendenciasService;
    private final ConsultaPronosticoService pronosticoService;

    @GetMapping("/mas-vendidos")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','VENDEDOR','CONTABILIDAD')")
    public MasVendidosResponse masVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) CriterioRanking criterio,
            @RequestParam(required = false) AgrupacionRanking agrupacion,
            @RequestParam(required = false) Integer limite) {
        return masVendidosService.consultar(desde, hasta, categoriaId, criterio, agrupacion, limite);
    }

    @GetMapping("/abc")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public Page<AbcVarianteResponse> abc(
            @RequestParam(required = false) ClaseAbc clase,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda no puede superar 60 caracteres") String q,
            @PageableDefault(size = 20, sort = "posicion") Pageable pageable) {
        return consultaService.listarAbc(clase, categoriaId, q, pageable);
    }

    @GetMapping("/abc/resumen")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public ResumenAbcResponse resumenAbc() {
        return consultaService.resumenAbc();
    }

    @GetMapping("/reorden")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public Page<ReordenVarianteResponse> reorden(
            @RequestParam(required = false) ClaseAbc clase,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda no puede superar 60 caracteres") String q,
            @RequestParam(required = false) MetodoReorden metodo,
            @RequestParam(required = false) Boolean enAlerta,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return consultaService.listarReorden(clase, categoriaId, q, metodo, enAlerta, pageable);
    }

    @GetMapping("/ejecuciones/ultima")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public ResponseEntity<EjecucionAnaliticaResponse> ultimaEjecucion() {
        return consultaService.ultimaEjecucion().map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/tendencias")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public TendenciaResponse tendencias(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long productoId) {
        return tendenciasService.consultar(desde, hasta, categoriaId, productoId);
    }

    @GetMapping("/pronosticos")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public Page<PronosticoProductoResponse> pronosticos(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) @Size(max = 60, message = "La búsqueda no puede superar 60 caracteres") String q,
            @RequestParam(required = false) MetodoPronostico metodo,
            @PageableDefault(size = 20, sort = "codigo") Pageable pageable) {
        return pronosticoService.listar(categoriaId, q, metodo, pageable);
    }

    @GetMapping("/pronosticos/{productoId}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTARIO','CONTABILIDAD')")
    public PronosticoDetalleResponse pronostico(@PathVariable Long productoId) {
        return pronosticoService.detalle(productoId);
    }

    @PostMapping("/recalcular")
    @PreAuthorize("hasRole('ADMIN')")
    public EjecucionAnaliticaResponse recalcular() {
        return recalculoService.recalcular();
    }
}
