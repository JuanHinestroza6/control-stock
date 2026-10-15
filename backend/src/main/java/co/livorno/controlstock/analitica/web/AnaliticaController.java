package co.livorno.controlstock.analitica.web;

import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.MasVendidosResponse;
import co.livorno.controlstock.analitica.servicio.MasVendidosService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analitica")
@RequiredArgsConstructor
public class AnaliticaController {

    private final MasVendidosService masVendidosService;

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
}
