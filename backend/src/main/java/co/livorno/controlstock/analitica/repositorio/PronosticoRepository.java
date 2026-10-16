package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.Pronostico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PronosticoRepository extends JpaRepository<Pronostico, Long> {
}
