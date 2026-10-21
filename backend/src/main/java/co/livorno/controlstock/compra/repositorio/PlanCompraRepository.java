package co.livorno.controlstock.compra.repositorio;

import co.livorno.controlstock.compra.modelo.EstadoPlan;
import co.livorno.controlstock.compra.modelo.PlanCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanCompraRepository extends JpaRepository<PlanCompra, Long> {

    /** Bloquea la fila del plan (toda transición; orden plan a variantes). Requiere transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlanCompra p where p.id = :id")
    Optional<PlanCompra> bloquear(@Param("id") Long id);

    /** Listado paginado; {@code estado} null trae todos. */
    @Query("select p from PlanCompra p where (:estado is null or p.estado = :estado)")
    Page<PlanCompra> buscar(@Param("estado") EstadoPlan estado, Pageable pageable);
}
