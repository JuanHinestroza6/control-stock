package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.CategoriaRequest;
import co.livorno.controlstock.catalogo.dto.CategoriaResponse;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.catalogo.repositorio.ConteoPorCategoria;
import co.livorno.controlstock.catalogo.repositorio.ProductoRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoriaService {

    private static final String DUPLICADA = "Ya existe una categoría con ese nombre.";
    private static final String CON_PRODUCTOS = "La categoría tiene productos asociados";

    private final CategoriaRepository repositorio;
    private final ProductoRepository productoRepositorio;

    public List<CategoriaResponse> listar() {
        Map<Long, Long> totales = new HashMap<>();
        for (ConteoPorCategoria c : productoRepositorio.contarPorCategoria()) {
            totales.put(c.categoriaId(), c.total());
        }
        return repositorio.findAllByOrderByNombreAsc().stream()
                .map(c -> CategoriaResponse.de(c, totales.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest req) {
        String nombre = req.nombre().strip();
        if (repositorio.existsByNombre(nombre)) {
            throw new ConflictoException(DUPLICADA);
        }
        return CategoriaResponse.de(guardar(new Categoria(nombre)), 0);
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest req) {
        Categoria categoria = buscar(id);
        String nombre = req.nombre().strip();
        if (repositorio.existsByNombreAndIdNot(nombre, id)) {
            throw new ConflictoException(DUPLICADA);
        }
        categoria.renombrar(nombre);
        return CategoriaResponse.de(guardar(categoria), productoRepositorio.countByCategoriaId(id));
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        if (productoRepositorio.existsByCategoriaId(id)) {
            throw new ConflictoException(CON_PRODUCTOS);
        }
        try {
            repositorio.delete(categoria);
            repositorio.flush();
        } catch (DataIntegrityViolationException e) {
            // Carrera: un producto se creó entre la verificación y el borrado (FK ON DELETE RESTRICT).
            throw new ConflictoException(CON_PRODUCTOS);
        }
    }

    private Categoria buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Categoría", id));
    }

    private Categoria guardar(Categoria categoria) {
        try {
            return repositorio.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException(DUPLICADA);
        }
    }
}
