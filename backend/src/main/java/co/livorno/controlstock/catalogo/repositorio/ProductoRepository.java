package co.livorno.controlstock.catalogo.repositorio;

import co.livorno.controlstock.catalogo.modelo.Producto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Carácter de escape de LIKE (no se usa la barra invertida: MySQL la interpreta en literales). */
    char ESCAPE = '!';

    boolean existsByCodigo(String codigo);

    boolean existsByCategoriaId(Long categoriaId);

    long countByCategoriaId(Long categoriaId);

    @Query("select p from Producto p join fetch p.categoria where p.id = :id")
    Optional<Producto> buscarConCategoria(@Param("id") Long id);

    /**
     * Listado filtrado y paginado, sin N+1 (la categoría viaja en el mismo SELECT). Los filtros nulos se ignoran.
     * {@code patron} debe construirse con {@link #patronContiene(String)}.
     */
    @Query(value = """
            select p from Producto p join fetch p.categoria c
            where (:patron is null or p.codigo like :patron escape '!' or p.nombre like :patron escape '!')
              and (:categoriaId is null or c.id = :categoriaId)
              and (:activo is null or p.activo = :activo)
            """,
            countQuery = """
            select count(p) from Producto p
            where (:patron is null or p.codigo like :patron escape '!' or p.nombre like :patron escape '!')
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:activo is null or p.activo = :activo)
            """)
    Page<Producto> buscar(@Param("patron") String patron, @Param("categoriaId") Long categoriaId,
            @Param("activo") Boolean activo, Pageable pageable);

    @Query("select new co.livorno.controlstock.catalogo.repositorio.ConteoPorCategoria(p.categoria.id, count(p)) "
            + "from Producto p group by p.categoria.id")
    List<ConteoPorCategoria> contarPorCategoria();

    /** Convierte el texto del usuario en un patrón "contiene" escapando %, _ y el carácter de escape. */
    static String patronContiene(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escapado = q.strip()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escapado + "%";
    }
}
