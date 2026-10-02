package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.ActualizarProductoRequest;
import co.livorno.controlstock.catalogo.dto.CrearProductoRequest;
import co.livorno.controlstock.catalogo.dto.ProductoDetalleResponse;
import co.livorno.controlstock.catalogo.dto.ProductoResumenResponse;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.modelo.Producto;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import co.livorno.controlstock.comun.seguridad.UsuarioActual;
import co.livorno.controlstock.comun.web.Ordenamiento;
import java.util.Locale;
import java.util.Set;
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

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final UsuarioActual usuarioActual;

    public Page<ProductoResumenResponse> listar(String q, Long categoriaId, Boolean activo, Pageable pageable) {
        Ordenamiento.validar(pageable, ORDEN_PERMITIDO);
        // Pendiente B6/B7: totalVariantes y stockDisponibleTotal van en 0 hasta que exista la tabla variante;
        // entonces se llenan con una consulta agregada sobre variantes activas (sin N+1).
        return productos.buscar(ProductoRepository.patronContiene(q), categoriaId, activo, pageable)
                .map(p -> ProductoResumenResponse.de(p, 0, 0));
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
        return detalle(guardar(producto));
    }

    @Transactional
    public ProductoDetalleResponse actualizar(Long id, ActualizarProductoRequest req) {
        Producto producto = buscar(id);
        Categoria categoria = categoria(req.categoriaId());
        producto.actualizarDatos(req.nombre(), categoria, req.precioCompra(), req.precioVenta(),
                req.leadTimeDias(), req.loteMinimo());
        return detalle(guardar(producto));
    }

    /**
     * Cambia el estado del producto; igual al actual es un no-op.
     * Pendiente B8: al desactivar, cascada a las variantes y 409 si alguna tiene stockReservado mayor a 0.
     */
    @Transactional
    public ProductoDetalleResponse cambiarEstado(Long id, boolean activo) {
        Producto producto = buscar(id);
        if (producto.isActivo() != activo) {
            if (activo) {
                producto.activar();
            } else {
                producto.desactivar();
            }
            guardar(producto);
        }
        return detalle(producto);
    }

    private ProductoDetalleResponse detalle(Producto p) {
        boolean verCosto = ROLES_CON_COSTO.contains(usuarioActual.obtener().rol());
        return ProductoDetalleResponse.de(p, verCosto, 0); // totalVariantes real: B6/B7
    }

    private Producto buscar(Long id) {
        return productos.buscarConCategoria(id).orElseThrow(() -> new RecursoNoEncontradoException("Producto", id));
    }

    private Categoria categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }

    private Producto guardar(Producto producto) {
        try {
            return productos.saveAndFlush(producto);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(CODIGO_DUPLICADO);
        }
    }
}
