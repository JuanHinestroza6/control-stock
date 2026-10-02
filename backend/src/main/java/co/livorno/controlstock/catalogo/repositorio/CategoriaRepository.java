package co.livorno.controlstock.catalogo.repositorio;

import co.livorno.controlstock.catalogo.modelo.Categoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Comparación según la collation de la columna (sin distinguir mayúsculas ni tildes). */
    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    List<Categoria> findAllByOrderByNombreAsc();
}
