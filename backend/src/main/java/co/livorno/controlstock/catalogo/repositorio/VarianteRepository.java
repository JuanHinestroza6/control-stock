package co.livorno.controlstock.catalogo.repositorio;

import co.livorno.controlstock.catalogo.modelo.Variante;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VarianteRepository extends JpaRepository<Variante, Long> {

    /** Lectura con bloqueo de fila (FOR UPDATE): ve siempre lo último confirmado. Lo usa la evaluación de alertas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variante v where v.id = :id")
    Optional<Variante> bloquearPorId(@Param("id") Long id);

    Optional<Variante> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Variante> findByProductoId(Long productoId);

    List<Variante> findByProductoIdAndActivoTrue(Long productoId);

    long countByProductoIdAndActivoTrue(Long productoId);

    /** Totales de variantes activas por producto en una sola consulta (sin N+1). */
    @Query("""
            select new co.livorno.controlstock.catalogo.repositorio.TotalesProducto(
                v.producto.id, count(v), coalesce(sum(v.stockFisico - v.stockReservado), 0))
            from Variante v
            where v.producto.id in :productoIds and v.activo = true
            group by v.producto.id
            """)
    List<TotalesProducto> totalesPorProducto(@Param("productoIds") Collection<Long> productoIds);
}
