package co.livorno.controlstock.venta.repositorio;

import co.livorno.controlstock.venta.modelo.Venta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

    @Override
    @EntityGraph(attributePaths = "vendedor")
    Page<Venta> findAll(Specification<Venta> spec, Pageable pageable);

    /** Venta con vendedor, anulador, líneas, variantes y productos en una sola consulta. */
    @Query("""
            select distinct v from Venta v
            join fetch v.vendedor
            left join fetch v.anuladaPor
            join fetch v.detalles d
            join fetch d.variante va
            join fetch va.producto
            where v.id = :id
            """)
    Optional<Venta> conDetalle(@Param("id") Long id);

    /** Bloquea la fila de la venta (anular y devolver se serializan; ADR-006). Requiere transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquear(@Param("id") Long id);
}
