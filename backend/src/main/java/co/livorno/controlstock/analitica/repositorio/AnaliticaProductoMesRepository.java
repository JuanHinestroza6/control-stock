package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.AnaliticaProductoMes;
import co.livorno.controlstock.analitica.modelo.AnaliticaProductoMesId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnaliticaProductoMesRepository extends JpaRepository<AnaliticaProductoMes, AnaliticaProductoMesId> {

    List<AnaliticaProductoMes> findByIdProductoIdOrderByIdPeriodoAsc(Long productoId);
}
