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
     * Kardex sin filtro de tipo, orden fijo {@code fecha desc, id desc} (el Pageable debe venir sin orden). Una sola
     * consulta con variante y producto. Rango semiabierto {@code [desde, hasta)}; filtros nulos se ignoran.
     */
    @Query(value = """
            select m from Movimiento m join fetch m.variante v join fetch v.producto p
            where (:varianteId is null or v.id = :varianteId)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            order by m.fecha desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movimiento m
            where (:varianteId is null or m.variante.id = :varianteId)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            """)
    Page<Movimiento> consultarTodos(@Param("varianteId") Long varianteId, @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, Pageable pageable);

    /** Igual que {@link #consultarTodos} pero con {@code m.tipo = :tipo} tipado, para usar idx_movimiento_tipo_fecha. */
    @Query(value = """
            select m from Movimiento m join fetch m.variante v join fetch v.producto p
            where m.tipo = :tipo
              and (:varianteId is null or v.id = :varianteId)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            order by m.fecha desc, m.id desc
            """,
            countQuery = """
            select count(m) from Movimiento m
            where m.tipo = :tipo
              and (:varianteId is null or m.variante.id = :varianteId)
              and (:desde is null or m.fecha >= :desde)
              and (:hasta is null or m.fecha < :hasta)
            """)
    Page<Movimiento> consultarPorTipo(@Param("tipo") TipoMovimiento tipo, @Param("varianteId") Long varianteId,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);

    default Page<Movimiento> buscar(Long varianteId, TipoMovimiento tipo, LocalDateTime desde, LocalDateTime hasta,
            Pageable pageable) {
        return tipo == null ? consultarTodos(varianteId, desde, hasta, pageable)
                : consultarPorTipo(tipo, varianteId, desde, hasta, pageable);
    }

    /**
     * Fecha de la última venta no anulada del producto con {@code fecha >= desde} (null si no hay). Una venta
     * (VENTA o VENTA_RESERVA) está anulada si existe ANULACION_VENTA con la misma referencia y variante. Usa
     * idx_movimiento_variante_tipo_fecha.
     */
    @Query("""
            select max(m.fecha) from Movimiento m
            where m.variante.producto.id = :productoId
              and m.tipo in (co.livorno.controlstock.inventario.modelo.TipoMovimiento.VENTA,
                             co.livorno.controlstock.inventario.modelo.TipoMovimiento.VENTA_RESERVA)
              and m.fecha >= :desde
              and not exists (select 1 from Movimiento a
                              where a.tipo = co.livorno.controlstock.inventario.modelo.TipoMovimiento.ANULACION_VENTA
                                and a.variante = m.variante and a.referenciaTipo = m.referenciaTipo
                                and a.referenciaId = m.referenciaId)
            """)
    LocalDateTime ultimaVentaNetaDesde(@Param("productoId") Long productoId, @Param("desde") LocalDateTime desde);

    /** Igual que {@link #ultimaVentaNetaDesde} sin límite inferior (todo el historial). */
    @Query("""
            select max(m.fecha) from Movimiento m
            where m.variante.producto.id = :productoId
              and m.tipo in (co.livorno.controlstock.inventario.modelo.TipoMovimiento.VENTA,
                             co.livorno.controlstock.inventario.modelo.TipoMovimiento.VENTA_RESERVA)
              and not exists (select 1 from Movimiento a
                              where a.tipo = co.livorno.controlstock.inventario.modelo.TipoMovimiento.ANULACION_VENTA
                                and a.variante = m.variante and a.referenciaTipo = m.referenciaTipo
                                and a.referenciaId = m.referenciaId)
            """)
    LocalDateTime ultimaVentaNeta(@Param("productoId") Long productoId);
}
