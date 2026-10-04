package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.EstadoReserva;
import co.livorno.controlstock.inventario.modelo.Reserva;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
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

    /** Reservas convertidas en una venta (orden de id). */
    List<Reserva> findByVentaIdOrderById(Long ventaId);

    /** Apartados y unidades ACTIVOS de una variante; vacío si no hay ninguno. */
    @Query("""
            select new co.livorno.controlstock.inventario.repositorio.ApartadosVariante(
                v.id, v.sku, count(r), sum(r.cantidad))
            from Reserva r join r.variante v
            where v.id = :varianteId
              and r.estado = co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA
            group by v.id, v.sku
            """)
    Optional<ApartadosVariante> apartadosActivosDeVariante(@Param("varianteId") Long varianteId);

    /** Variantes de un producto con apartados ACTIVOS, por id de variante (usar Pageable para limitar). */
    @Query("""
            select new co.livorno.controlstock.inventario.repositorio.ApartadosVariante(
                v.id, v.sku, count(r), sum(r.cantidad))
            from Reserva r join r.variante v
            where v.producto.id = :productoId
              and r.estado = co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA
            group by v.id, v.sku
            order by v.id
            """)
    List<ApartadosVariante> apartadosActivosPorProducto(@Param("productoId") Long productoId, Pageable pageable);

    /** Reserva con variante y producto en un solo SELECT. */
    @Query("select r from Reserva r join fetch r.variante v join fetch v.producto where r.id = :id")
    Optional<Reserva> conVariante(@Param("id") Long id);

    /**
     * Listado filtrado; los filtros nulos se ignoran. {@code patron} (nombre o contacto del cliente, SKU) debe venir de
     * {@code ProductoRepository.patronContiene}. {@code limite} no nulo = solo ACTIVA con {@code venceEn < limite}.
     */
    @Query(value = """
            select r from Reserva r join fetch r.variante v join fetch v.producto p
            where (:estado is null or r.estado = :estado)
              and (:varianteId is null or v.id = :varianteId)
              and (:patron is null or r.clienteNombre like :patron escape '!'
                   or r.clienteContacto like :patron escape '!' or v.sku like :patron escape '!')
              and (:limite is null or (r.estado = co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA
                   and r.venceEn < :limite))
            """,
            countQuery = """
            select count(r) from Reserva r join r.variante v
            where (:estado is null or r.estado = :estado)
              and (:varianteId is null or v.id = :varianteId)
              and (:patron is null or r.clienteNombre like :patron escape '!'
                   or r.clienteContacto like :patron escape '!' or v.sku like :patron escape '!')
              and (:limite is null or (r.estado = co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA
                   and r.venceEn < :limite))
            """)
    Page<Reserva> buscar(@Param("estado") EstadoReserva estado, @Param("varianteId") Long varianteId,
            @Param("patron") String patron, @Param("limite") LocalDateTime limite, Pageable pageable);

    /**
     * Reservas cerradas (no ACTIVAS) con {@code cerradaEn <= limite} que aún conservan datos del cliente (usa
     * idx_reserva_estado_cerrada). Pasar un Pageable de una sola página como lote.
     */
    @Query("""
            select r from Reserva r
            where r.estado <> co.livorno.controlstock.inventario.modelo.EstadoReserva.ACTIVA
              and r.cerradaEn <= :limite
              and (r.clienteNombre <> :marca or r.clienteContacto <> :marca)
            order by r.id
            """)
    List<Reserva> pendientesDeAnonimizar(@Param("limite") LocalDateTime limite, @Param("marca") String marca,
            Pageable pageable);
}
