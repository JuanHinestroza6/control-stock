package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.GenerarVariantesRequest;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse.Motivo;
import co.livorno.controlstock.catalogo.dto.GenerarVariantesResponse.Omitida;
import co.livorno.controlstock.catalogo.dto.VarianteResponse;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.modelo.Talla;
import co.livorno.controlstock.catalogo.modelo.Variante;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.ExcepcionNegocio;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VarianteService {

    private static final Comparator<Variante> ORDEN = Comparator.comparing(Variante::getTalla)
            .thenComparing(Variante::getColor);

    private final ProductoRepository productos;
    private final VarianteRepository variantes;

    public List<VarianteResponse> listar(Long productoId, boolean incluirInactivas) {
        if (!productos.existsById(productoId)) {
            throw new RecursoNoEncontradoException("Producto", productoId);
        }
        List<Variante> lista = incluirInactivas ? variantes.findByProductoId(productoId)
                : variantes.findByProductoIdAndActivoTrue(productoId);
        return lista.stream().sorted(ORDEN).map(VarianteResponse::de).toList();
    }

    /** Crea el producto cartesiano talla x color; idempotente: las combinaciones existentes se reportan como omitidas. */
    @Transactional
    public GenerarVariantesResponse generar(Long productoId, GenerarVariantesRequest req) {
        Producto producto = productos.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto", productoId));
        if (!producto.isActivo()) {
            throw new ExcepcionNegocio("El producto " + producto.getCodigo()
                    + " está inactivo; actívalo antes de generar variantes.");
        }
        Set<Talla> tallas = EnumSet.copyOf(req.tallas());
        Set<String> colores = new TreeSet<>();
        req.colores().forEach(c -> colores.add(GeneradorSku.normalizarColor(c)));

        Map<String, Variante> existentes = new HashMap<>();
        Set<Talla> tallasDelProducto = EnumSet.noneOf(Talla.class);
        for (Variante v : variantes.findByProductoId(productoId)) {
            existentes.put(clave(v.getTalla(), v.getColor()), v);
            tallasDelProducto.add(v.getTalla());
        }
        tallasDelProducto.addAll(tallas);
        if (tallasDelProducto.contains(Talla.UNICA) && tallasDelProducto.size() > 1) {
            throw new ExcepcionNegocio("La talla UNICA no se puede combinar con otras tallas en un mismo producto.");
        }

        Set<String> skusDelLote = new HashSet<>();
        List<Variante> nuevas = new ArrayList<>();
        List<Omitida> omitidas = new ArrayList<>();
        for (Talla talla : tallas) {
            for (String color : colores) {
                Variante existente = existentes.get(clave(talla, color));
                if (existente != null) {
                    omitidas.add(new Omitida(talla, color, existente.getSku(),
                            existente.isActivo() ? Motivo.EXISTE : Motivo.EXISTE_INACTIVA));
                    continue;
                }
                String sku = GeneradorSku.generar(producto.getCodigo(), talla.name(), color,
                        c -> skusDelLote.contains(c) || variantes.existsBySku(c));
                skusDelLote.add(sku);
                nuevas.add(new Variante(producto, sku, talla, color, req.stockMinimo()));
            }
        }
        try {
            variantes.saveAllAndFlush(nuevas);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("Otra operación creó variantes al mismo tiempo. Intenta de nuevo.");
        }
        return new GenerarVariantesResponse(nuevas.stream().sorted(ORDEN).map(VarianteResponse::de).toList(),
                omitidas);
    }

    private static String clave(Talla talla, String color) {
        return talla.name() + "|" + color;
    }
}
