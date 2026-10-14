package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.EjecucionAnalitica;
import co.livorno.controlstock.analitica.modelo.EstadoEjecucion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjecucionAnaliticaRepository extends JpaRepository<EjecucionAnalitica, Long> {

    /** Ultima ejecucion, cualquiera sea su estado. */
    Optional<EjecucionAnalitica> findFirstByOrderByIniciadaEnDescIdDesc();

    /** Ultima ejecucion con el estado dado (por ejemplo, la ultima EXITOSA). */
    Optional<EjecucionAnalitica> findFirstByEstadoOrderByIniciadaEnDescIdDesc(EstadoEjecucion estado);

    List<EjecucionAnalitica> findByEstado(EstadoEjecucion estado);
}
