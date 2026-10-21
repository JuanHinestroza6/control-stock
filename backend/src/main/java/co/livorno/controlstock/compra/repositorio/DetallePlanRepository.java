package co.livorno.controlstock.compra.repositorio;

import co.livorno.controlstock.compra.modelo.DetallePlan;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetallePlanRepository extends JpaRepository<DetallePlan, Long> {

    /** Σ cantidadAprobada de las líneas de planes APROBADO de la variante (0 si no hay). */
    @Query("""
            select coalesce(sum(d.cantidadAprobada), 0) from DetallePlan d
            where d.variante.id = :varianteId
              and d.plan.estado = co.livorno.controlstock.compra.modelo.EstadoPlan.APROBADO
            """)
    long enCamino(@Param("varianteId") Long varianteId);

    /** En camino de todas las variantes con pedido aprobado, por id de variante. */
    @Query("""
            select new co.livorno.controlstock.compra.repositorio.EnCaminoVariante(
                d.variante.id, sum(d.cantidadAprobada))
            from DetallePlan d
            where d.plan.estado = co.livorno.controlstock.compra.modelo.EstadoPlan.APROBADO
            group by d.variante.id
            order by d.variante.id
            """)
    List<EnCaminoVariante> enCaminoPorVariante();

    /** Lo mismo que {@link #enCaminoPorVariante()} pero solo para las variantes dadas (detalle de un plan). */
    @Query("""
            select new co.livorno.controlstock.compra.repositorio.EnCaminoVariante(
                d.variante.id, sum(d.cantidadAprobada))
            from DetallePlan d
            where d.plan.estado = co.livorno.controlstock.compra.modelo.EstadoPlan.APROBADO
              and d.variante.id in :ids
            group by d.variante.id
            order by d.variante.id
            """)
    List<EnCaminoVariante> enCaminoDe(@Param("ids") Collection<Long> ids);

    /** Ids de las variantes (entre las dadas) que ya están en algún plan BORRADOR, para omitirlas al sugerir. */
    @Query("""
            select distinct d.variante.id from DetallePlan d
            where d.plan.estado = co.livorno.controlstock.compra.modelo.EstadoPlan.BORRADOR
              and d.variante.id in :ids
            """)
    List<Long> varianteIdsEnBorrador(@Param("ids") Collection<Long> ids);
}
