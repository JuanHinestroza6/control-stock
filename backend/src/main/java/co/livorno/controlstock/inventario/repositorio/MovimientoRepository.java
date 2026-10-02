package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.Movimiento;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Kardex inmutable: extiende Repository (no JpaRepository) para no exponer delete ni actualización masiva. */
public interface MovimientoRepository extends Repository<Movimiento, Long> {

    Movimiento save(Movimiento movimiento);

    Optional<Movimiento> findById(Long id);
}
