package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.dto.ParametrosModelo;
import co.livorno.controlstock.analitica.dto.PronosticoDetalleResponse;
import co.livorno.controlstock.analitica.dto.PronosticoProductoResponse;
import co.livorno.controlstock.analitica.dto.PronosticoVarianteResponse;
import co.livorno.controlstock.analitica.dto.PuntoPronostico;
import co.livorno.controlstock.analitica.modelo.MetodoPronostico;
import co.livorno.controlstock.analitica.modelo.MotivoSinHoltWinters;
import co.livorno.controlstock.analitica.repositorio.ConsultasPronostico;
import co.livorno.controlstock.analitica.repositorio.FilaPronosticoProducto;
import co.livorno.controlstock.analitica.repositorio.FilaPronosticoVariante;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.web.Ordenamiento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del último pronóstico por producto (ADR-010). Solo lectura; WAPE y participación como fracción 0-1. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaPronosticoService {

    public static final Set<String> ORDEN = Set.of("codigo", "nombre", "errorWape", "unidadesUltimos12m");
    private static final Map<String, String> COLUMNA = Map.of(
            "codigo", "p.codigo",
            "nombre", "p.nombre",
            "errorWape", "ap.error_wape",
            "unidadesUltimos12m", "ap.unidades_observadas_12m");

    private final ConsultasPronostico consultas;

    public Page<PronosticoProductoResponse> listar(Long categoriaId, String q, MetodoPronostico metodo,
            Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN);
        String metodoTexto = metodo != null ? metodo.name() : null;
        String patron = ProductoRepository.patronContiene(q);
        long total = consultas.contar(categoriaId, patron, metodoTexto);
        List<FilaPronosticoProducto> filas = consultas.pagina(categoriaId, patron, metodoTexto, orden(pageable),
                pageable.getPageSize(), pageable.getOffset());
        Map<Long, List<PuntoPronostico>> proximos = consultas
                .pronosticoProductos(filas.stream().map(FilaPronosticoProducto::productoId).toList());
        List<PronosticoProductoResponse> contenido = filas.stream()
                .map(f -> producto(f, proximos.getOrDefault(f.productoId(), List.of()))).toList();
        return new PageImpl<>(contenido, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
    }

    public PronosticoDetalleResponse detalle(long productoId) {
        FilaPronosticoProducto f = consultas.producto(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pronóstico del producto", productoId));
        List<PuntoPronostico> pronostico = consultas.pronosticoProductos(List.of(productoId))
                .getOrDefault(productoId, List.of());
        ParametrosModelo parametros = f.alfa() == null && f.beta() == null && f.gamma() == null ? null
                : new ParametrosModelo(f.alfa(), f.beta(), f.gamma());
        PronosticoProductoResponse base = producto(f, pronostico);
        return new PronosticoDetalleResponse(base.productoId(), base.codigo(), base.nombre(), base.categoria(),
                base.metodo(), base.errorWape(), base.wapeSes(), base.wapeHw(), base.motivoSinHw(),
                base.mesesHistoria(), base.unidadesUltimos12m(), base.unidadesCorregidas12m(), base.proximos(),
                parametros, consultas.serie(productoId), pronostico, variantes(productoId, pronostico),
                f.calculadoEn());
    }

    private List<PronosticoVarianteResponse> variantes(long productoId, List<PuntoPronostico> totalProducto) {
        Map<Long, List<FilaPronosticoVariante>> porVariante = new LinkedHashMap<>();
        consultas.variantes(productoId)
                .forEach(v -> porVariante.computeIfAbsent(v.varianteId(), k -> new ArrayList<>()).add(v));
        BigDecimal total = totalProducto.stream().map(PuntoPronostico::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PronosticoVarianteResponse> lista = new ArrayList<>();
        for (List<FilaPronosticoVariante> filas : porVariante.values()) {
            FilaPronosticoVariante p = filas.get(0);
            BigDecimal suma = filas.stream().map(FilaPronosticoVariante::cantidad)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal participacion = total.signum() == 0 ? BigDecimal.ZERO.setScale(4)
                    : suma.divide(total, 4, RoundingMode.HALF_UP);
            lista.add(new PronosticoVarianteResponse(p.varianteId(), p.sku(), Talla.valueOf(p.talla()), p.color(),
                    participacion, filas.stream()
                            .map(v -> new PuntoPronostico(v.periodo().toString(), v.cantidad())).toList()));
        }
        lista.sort(Comparator.comparing(PronosticoVarianteResponse::participacion).reversed()
                .thenComparing(PronosticoVarianteResponse::sku));
        return lista;
    }

    private static PronosticoProductoResponse producto(FilaPronosticoProducto f, List<PuntoPronostico> proximos) {
        MetodoPronostico metodo = MetodoPronostico.valueOf(f.metodo());
        return new PronosticoProductoResponse(f.productoId(), f.codigo(), f.nombre(), f.categoria(),
                metodo == MetodoPronostico.SIN_HISTORIA ? null : metodo, f.errorWape(), f.wapeSes(), f.wapeHw(),
                f.motivoSinHw() != null ? MotivoSinHoltWinters.valueOf(f.motivoSinHw()) : null, f.mesesHistoria(),
                f.unidadesObservadas12m(), f.unidadesCorregidas12m(), proximos);
    }

    /** Cláusula ORDER BY con las columnas de la lista blanca y el id como desempate estable. */
    private static String orden(Pageable pageable) {
        List<String> partes = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            partes.add(COLUMNA.get(o.getProperty()) + (o.isAscending() ? " ASC" : " DESC"));
        }
        if (partes.isEmpty()) {
            partes.add("p.codigo ASC");
        }
        partes.add("p.id ASC");
        return String.join(", ", partes);
    }
}
