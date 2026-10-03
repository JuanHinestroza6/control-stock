package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.Reserva;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** Bloquea la fila de la reserva (toda transición; orden de bloqueo reserva a variante). Requiere transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> bloquear(@Param("id") Long id);

    /** Bloquea varias reservas en orden de id (evita interbloqueos). Requiere transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.id in :ids order by r.id")
    List<Reserva> bloquearVarias(@Param("ids") Collection<Long> ids);

    /** Ids de reservas ACTIVAS vencidas, de la más antigua a la más reciente (usa idx_reserva_estado_vence). */
    @Query("""
            select r.id from Reserva r
            where r.estado = co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA and r.venceEn <= :ahora
            order by r.venceEn, r.id
            """)
    List<Long> idsVencidas(@Param("ahora") LocalDateTime ahora, Pageable pageable);
}
