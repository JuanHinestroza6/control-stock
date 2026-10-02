package co.livorno.controlstock.catalogo.servicio;

import co.livorno.controlstock.catalogo.dto.CategoriaRequest;
import co.livorno.controlstock.catalogo.dto.CategoriaResponse;
import co.livorno.controlstock.catalogo.modelo.Categoria;
import co.livorno.controlstock.catalogo.repositorio.CategoriaRepository;
import co.livorno.controlstock.comun.excepcion.ConflictoException;
import co.livorno.controlstock.comun.excepcion.RecursoNoEncontradoException;
import java.util.List;
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

    public List<CategoriaResponse> listar() {
        // totalProductos queda en 0 hasta que exista Producto (B3), que lo calculará con una consulta agregada.
        return repositorio.findAllByOrderByNombreAsc().stream()
                .map(c -> CategoriaResponse.de(c, 0))
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
        return CategoriaResponse.de(guardar(categoria), 0);
    }

    @Transactional
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        try {
            repositorio.delete(categoria);
            repositorio.flush();
        } catch (DataIntegrityViolationException e) {
            // FK producto.categoria_id ON DELETE RESTRICT
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
