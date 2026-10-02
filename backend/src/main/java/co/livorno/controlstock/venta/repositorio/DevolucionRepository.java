package co.livorno.controlstock.venta.repositorio;

import co.livorno.controlstock.venta.modelo.Devolucion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {

    boolean existsByDetalleVentaVentaId(Long ventaId);

    /** Devoluciones de todas las líneas de una venta, con usuario y línea, en una consulta. */
    @Query("""
            select d from Devolucion d
            join fetch d.usuario
            join fetch d.detalleVenta dv
            where dv.venta.id = :ventaId
            order by d.fecha, d.id
            """)
    List<Devolucion> porVenta(@Param("ventaId") Long ventaId);

    @Query("select coalesce(sum(d.cantidad), 0) from Devolucion d where d.detalleVenta.id = :detalleId")
    int sumaDevuelta(@Param("detalleId") Long detalleId);
}
