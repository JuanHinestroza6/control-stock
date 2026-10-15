package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.config.PropiedadesAnalitica;
import co.livorno.controlstock.analitica.dto.AbcVarianteResponse;
import co.livorno.controlstock.analitica.dto.ClaseResumen;
import co.livorno.controlstock.analitica.dto.EjecucionAnaliticaResponse;
import co.livorno.controlstock.analitica.dto.ReordenVarianteResponse;
import co.livorno.controlstock.analitica.dto.ResumenAbcResponse;
import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import co.livorno.controlstock.analitica.repositorio.AnaliticaVarianteRepository;
import co.livorno.controlstock.analitica.repositorio.EjecucionAnaliticaRepository;
import co.livorno.controlstock.analitica.repositorio.ResumenClaseFila;
import co.livorno.controlstock.analitica.modelo.ClaseAbc;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.usuario.modelo.Usuario;
import co.livorno.controlstock.usuario.repositorio.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de la analítica (ABC, punto de reorden, última ejecución). Leen la instantánea {@code analitica_variante}
 * del último cálculo, unida al estado actual de la variante: lo que se muestra es lo que se aplicó (ADR-009).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaAnaliticaService {

    public static final Set<String> ORDEN_ABC = Set.of("posicion", "ingresoNeto", "sku");
    public static final Set<String> ORDEN_REORDEN = Set.of("sku", "puntoReorden", "demandaMedia");

    private static final Map<String, String> PROPIEDAD = Map.of(
            "posicion", "posicion",
            "ingresoNeto", "ingresoNeto",
            "sku", "variante.sku",
            "puntoReorden", "puntoReorden",
            "demandaMedia", "demandaMedia");
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final AnaliticaVarianteRepository instantanea;
    private final EjecucionAnaliticaRepository ejecuciones;
    private final UsuarioRepository usuarios;
    private final PropiedadesAnalitica propiedades;

    public Page<AbcVarianteResponse> listarAbc(ClaseAbc clase, Long categoriaId, String q, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_ABC);
        return instantanea.consultarAbc(clase != null ? Character.valueOf(clase.name().charAt(0)) : null, categoriaId,
                ProductoRepository.patronContiene(q), traducir(pageable, "posicion"))
                .map(AbcVarianteResponse::de);
    }

    public Page<ReordenVarianteResponse> listarReorden(ClaseAbc clase, Long categoriaId, String q,
            MetodoReorden metodo, Boolean enAlerta, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_REORDEN);
        int modoAlerta = enAlerta == null ? 0 : enAlerta ? 1 : 2;
        return instantanea.consultarReorden(clase != null ? Character.valueOf(clase.name().charAt(0)) : null, categoriaId,
                ProductoRepository.patronContiene(q), metodo, modoAlerta,
                traducir(pageable, "sku")).map(ReordenVarianteResponse::de);
    }

    public ResumenAbcResponse resumenAbc() {
        Optional<EjecucionAnalitica> ultima = ejecuciones
                .findFirstByEstadoOrderByIniciadaEnDescIdDesc(EstadoEjecucion.EXITOSA);
        Map<String, ResumenClaseFila> filas = new java.util.HashMap<>();
        instantanea.resumenPorClase().forEach(f -> filas.put(f.clase(), f));
        BigDecimal total = filas.values().stream().map(ResumenClaseFila::ingreso)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        List<ClaseResumen> clases = List.of("A", "B", "C").stream().map(c -> {
            ResumenClaseFila f = filas.get(c);
            BigDecimal ingreso = f != null ? f.ingreso().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
            BigDecimal participacion = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : ingreso.multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
            return new ClaseResumen(c, f != null ? f.variantes() : 0L, ingreso, participacion);
        }).toList();
        if (ultima.isEmpty()) {
            return new ResumenAbcResponse(null, null, null, total, clases);
        }
        EjecucionAnalitica e = ultima.get();
        LocalDate hasta = e.getFechaCorte().minusDays(1);
        LocalDate desde = e.getFechaCorte().minusDays(propiedades.ventanaAbcDias());
        return new ResumenAbcResponse(respuesta(e), desde, hasta, total, clases);
    }

    /** Última ejecución (cualquier estado), o vacío si la analítica nunca corrió. */
    public Optional<EjecucionAnaliticaResponse> ultimaEjecucion() {
        return ejecuciones.findFirstByOrderByIniciadaEnDescIdDesc().map(this::respuesta);
    }

    public EjecucionAnaliticaResponse respuesta(EjecucionAnalitica e) {
        String nombre = e.getUsuarioId() == null ? null
                : usuarios.findById(e.getUsuarioId()).map(Usuario::getNombre).orElse(null);
        return EjecucionAnaliticaResponse.de(e, nombre);
    }

    /** Traduce el orden de la API a propiedades de la entidad y agrega la variante como desempate estable. */
    private static Pageable traducir(Pageable pageable, String ordenPorDefecto) {
        Sort orden = pageable.getSort().isSorted()
                ? Sort.by(pageable.getSort().stream()
                        .map(o -> o.withProperty(PROPIEDAD.get(o.getProperty()))).toList())
                : Sort.by(PROPIEDAD.get(ordenPorDefecto));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden.and(Sort.by("varianteId")));
    }
}
