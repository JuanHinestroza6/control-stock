package co.livorno.controlstock.venta.repositorio;

import co.livorno.controlstock.venta.modelo.Venta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** Bloquea la fila de la venta (anular y devolver se serializan; ADR-006). Requiere transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquear(@Param("id") Long id);
}
