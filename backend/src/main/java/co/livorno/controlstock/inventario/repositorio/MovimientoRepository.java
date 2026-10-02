package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.Movimiento;
import co.livorno.controlstock.inventario.modelo.TipoMovimiento;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Kardex inmutable: extiende Repository (no JpaRepository) para no exponer delete ni actualización masiva. */
public interface MovimientoRepository extends Repository<Movimiento, Long> {

    Movimiento save(Movimiento movimiento);

    Optional<Movimiento> findById(Long id);

    /**
     * Kardex filtrado, orden fijo {@code fecha desc, id desc} (el Pageable debe venir sin orden). Una sola consulta con
     * variante y producto. Rango semiabierto {@code [desde, hasta)}; filtros nulos se ignoran.
     */
    @Query(value = """
            select m from Movimiento m join fetch m.variante v join fetch v.producto p
            where (:varianteId is null or v.id = :varianteId)
              and (:tipo is null or cast(m.tipo as string) = :tipo)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            order by m.fecha desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movimiento m
            where (:varianteId is null or m.variante.id = :varianteId)
              and (:tipo is null or cast(m.tipo as string) = :tipo)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            """)
    Page<Movimiento> consultar(@Param("varianteId") Long varianteId, @Param("tipo") String tipo,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);

    /** Versión tipada (Hibernate no infiere el tipo de un enum nulo en {@code :tipo is null}). */
    default Page<Movimiento> buscar(Long varianteId, TipoMovimiento tipo, LocalDateTime desde, LocalDateTime hasta,
            Pageable pageable) {
        return consultar(varianteId, tipo != null ? tipo.name() : null, desde, hasta, pageable);
    }
}
