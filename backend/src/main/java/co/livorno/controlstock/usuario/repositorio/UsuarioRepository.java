package co.livorno.controlstock.usuario.repositorio;

import co.livorno.controlstock.usuario.modelo.Rol;
import co.livorno.controlstock.usuario.modelo.Usuario;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByRol(Rol rol);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Listado filtrado y paginado; los filtros nulos se ignoran. {@code patron} viene de {@link #patronContiene}. */
    @Query("""
            select u from Usuario u
            where (:patron is null or u.nombre like :patron escape '!' or u.email like :patron escape '!')
              and (:rol is null or u.rol = :rol)
              and (:activo is null or u.activo = :activo)
            """)
    Page<Usuario> buscar(@Param("patron") String patron, @Param("rol") Rol rol, @Param("activo") Boolean activo,
            Pageable pageable);

    /** Bloquea (SELECT ... FOR UPDATE) a los ADMIN activos para serializar cambios que podrían dejar cero. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.rol = co.livorno.controlstock.usuario.modelo.Rol.ADMIN "
            + "and u.activo = true order by u.id")
    List<Usuario> bloquearAdminsActivos();

    /** Convierte el texto del usuario en un patrón "contiene" escapando %, _ y el carácter de escape (!). */
    static String patronContiene(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
