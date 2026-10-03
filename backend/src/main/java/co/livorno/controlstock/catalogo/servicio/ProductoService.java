package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.ActualizarProductoRequest;
import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.dto.ProductoResumenResponse;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.catalogo.repositorio.TotalesProducto;
import co.livorno.controlstock.catalogo.repositorio.VarianteRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import co.livorno.controlstock.catalogo.modelo.Variante;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductoService {

    public static final Set<String> ORDEN_PERMITIDO = Set.of("codigo", "nombre", "precioVenta", "creadoEn");
    private static final Set<String> ROLES_CON_COSTO = Set.of("ADMIN", "INVENTARIO", "CONTABILIDAD");
    private static final String CODIGO_DUPLICADO = "Ya existe un producto con ese código.";
    private static final String CONFLICTO_ACTUALIZAR =
            "No se pudo guardar el producto: la categoría ya no existe o el producto fue modificado en paralelo.";

    private final ProductoRepository productos;
    private final VarianteRepository variantes;
    private final CategoriaRepository categorias;
    private final UsuarioActual usuarioActual;
    private final ConsultaReservasActivas reservasActivas;

    public Page<ProductoResumenResponse> listar(String q, Long categoriaId, Boolean activo, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        Page<Producto> pagina = productos.buscar(ProductoRepository.patronContiene(q), categoriaId, activo, pageable);
        // Una sola consulta agregada (variantes activas) para toda la página.
        Map<Long, TotalesProducto> totales = pagina.isEmpty() ? Map.of()
                : variantes.totalesPorProducto(pagina.map(Producto::getId).getContent()).stream()
                        .collect(Collectors.toMap(TotalesProducto::productoId, Function.identity()));
        return pagina.map(p -> {
            TotalesProducto t = totales.get(p.getId());
            return ProductoResumenResponse.de(p, t != null ? t.totalVariantes() : 0,
                    t != null ? t.stockDisponible() : 0);
        });
    }

    public ProductoDetalleResponse obtener(Long id) {
        return detalle(buscar(id));
    }

    @Transactional
    public ProductoDetalleResponse crear(CrearProductoRequest req) {
        String codigo = req.codigo().strip().toUpperCase(Locale.ROOT);
        Categoria categoria = categoria(req.categoriaId());
        if (productos.existsByCodigo(codigo)) {
            throw new ConflictoException(CODIGO_DUPLICADO);
        }
        Producto producto = new Producto(codigo, req.nombre(), categoria, req.precioCompra(), req.precioVenta(),
                req.leadTimeDias(), req.loteMinimo());
        return detalle(guardar(producto, CODIGO_DUPLICADO));
    }

    @Transactional
    public ProductoDetalleResponse actualizar(Long id, ActualizarProductoRequest req) {
        Producto producto = buscar(id);
        Categoria categoria = categoria(req.categoriaId());
        producto.actualizarDatos(req.nombre(), categoria, req.precioCompra(), req.precioVenta(),
                req.leadTimeDias(), req.loteMinimo());
        return detalle(guardar(producto, CONFLICTO_ACTUALIZAR));
    }

    /**
     * Cambia el estado del producto; igual al actual es un no-op.
     * Al desactivar: 409 si alguna variante tiene stockReservado mayor a 0; si no, se desactivan todas sus variantes.
     * Reactivar el producto NO reactiva sus variantes.
     */
    @Transactional
    public ProductoDetalleResponse cambiarEstado(Long id, boolean activo) {
        Producto producto = buscar(id);
        if (producto.isActivo() != activo) {
            if (activo) {
                producto.activar();
            } else {
                reservasActivas.primeraVarianteConApartados(id).ifPresent(a -> {
                    throw new ConflictoException("No se puede desactivar el producto: la variante " + a.sku()
                            + " tiene " + a.apartados() + " apartados activos (" + a.unidades() + " unidades).");
                });
                List<Variante> deLaFamilia = variantes.findByProductoId(id);
                deLaFamilia.stream().filter(v -> v.getStockReservado() > 0).findFirst().ifPresent(v -> {
                    throw new ConflictoException("No se puede desactivar el producto: la variante " + v.getSku()
                            + " tiene " + v.getStockReservado() + " unidades en apartados activos.");
                });
                deLaFamilia.forEach(Variante::desactivar);
                producto.desactivar();
            }
            guardar(producto, CONFLICTO_ACTUALIZAR);
        }
        return detalle(producto);
    }

    private ProductoDetalleResponse detalle(Producto p) {
        boolean verCosto = ROLES_CON_COSTO.contains(usuarioActual.obtener().rol());
        return ProductoDetalleResponse.de(p, verCosto, variantes.countByProductoIdAndActivoTrue(p.getId()));
    }

    private Producto buscar(Long id) {
        return productos.buscarConCategoria(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }

    private Categoria categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }

    private Producto guardar(Producto producto, String mensajeConflicto) {
        try {
            return productos.saveAndFlush(producto);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(mensajeConflicto);
        }
    }
}
