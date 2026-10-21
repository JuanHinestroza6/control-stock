package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.Pronostico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PronosticoRepository extends JpaRepository<Pronostico, Long> {

    /** Pronóstico mensual desagregado por variante (la última ejecución), por variante y periodo ascendentes. */
    List<Pronostico> findByVarianteIdIsNotNullOrderByVarianteIdAscPeriodoAsc();
}
