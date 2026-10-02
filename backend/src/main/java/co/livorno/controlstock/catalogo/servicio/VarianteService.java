package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.ActualizarVarianteRequest;
import co.livorno.controlstock.catalogo.dto.MatrizResponse;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
        // Colores que solo difieren en tildes son el mismo color (la BD los trata como iguales): se conserva el primero.
        Map<String, String> porClave = new TreeMap<>();
        req.colores().forEach(c -> {
            String color = GeneradorSku.normalizarColor(c);
            porClave.putIfAbsent(GeneradorSku.claveColor(color), color);
        });
        Collection<String> colores = porClave.values();

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

    @Transactional
    public VarianteResponse actualizar(Long id, ActualizarVarianteRequest req) {
        Variante v = buscar(id);
        v.actualizarStockMinimo(req.stockMinimo());
        return VarianteResponse.de(variantes.saveAndFlush(v));
    }

    /** Igual al estado actual es no-op. Desactivar con reservado mayor a 0 da 409; activar con producto inactivo, 422. */
    @Transactional
    public VarianteResponse cambiarEstado(Long id, boolean activo) {
        Variante v = buscar(id);
        if (v.isActivo() != activo) {
            if (activo) {
                if (!v.getProducto().isActivo()) {
                    throw new ExcepcionNegocio("Activa primero el producto " + v.getProducto().getCodigo() + ".");
                }
                v.activar();
            } else {
                if (v.getStockReservado() > 0) {
                    throw new ConflictoException("La variante " + v.getSku() + " tiene " + v.getStockReservado()
                            + " unidades en apartados activos.");
                }
                v.desactivar();
            }
            variantes.saveAndFlush(v);
        }
        return VarianteResponse.de(v);
    }

    public MatrizResponse matriz(Long productoId, boolean incluirInactivas) {
        List<VarianteResponse> lista = listar(productoId, incluirInactivas);
        List<Talla> tallas = lista.stream().map(VarianteResponse::talla).distinct().sorted().toList();
        List<String> colores = lista.stream().map(VarianteResponse::color).distinct().sorted().toList();
        List<MatrizResponse.Celda> celdas = lista.stream()
                .map(v -> new MatrizResponse.Celda(v.talla(), v.color(), v.id(), v.sku(), v.stockFisico(),
                        v.stockReservado(), v.stockDisponible(), v.estadoStock(), v.activo()))
                .toList();
        return new MatrizResponse(productoId, tallas, colores, celdas);
    }

    private Variante buscar(Long id) {
        return variantes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Variante", id));
    }

    private static String clave(Talla talla, String color) {
        return talla.name() + "|" + GeneradorSku.claveColor(color);
    }
}
