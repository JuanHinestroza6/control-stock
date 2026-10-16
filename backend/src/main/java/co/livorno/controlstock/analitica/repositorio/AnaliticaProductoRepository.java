package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.AnaliticaProducto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnaliticaProductoRepository extends JpaRepository<AnaliticaProducto, Long> {
}
