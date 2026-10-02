package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.catalogo.modelo.EstadoStock;
import co.livorno.controlstock.catalogo.modelo.Variante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Consulta de solo lectura del inventario actual (el stock solo cambia vía MovimientoService). */
public interface InventarioRepository extends Repository<Variante, Long> {

    /**
     * Variantes con su producto y categoría en un solo SELECT. Los filtros nulos se ignoran. {@code patron} se
     * construye con {@code ProductoRepository.patronContiene}. El estado usa el mismo umbral que
     * {@code Variante.estadoStock()}.
     */
    @Query(value = """
            select v from Variante v join fetch v.producto p join fetch p.categoria c
            where (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:categoriaId is null or c.id = :categoriaId)
              and (:incluirInactivas = true or v.activo = true)
              and (:estado is null
                   or (:estado = 'AGOTADO'
                       and v.stockFisico - v.stockReservado <= 0)
                   or (:estado = 'BAJO'
                       and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado <= coalesce(v.puntoReorden, v.stockMinimo))
                   or (:estado = 'OK'
                       and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado > coalesce(v.puntoReorden, v.stockMinimo)))
            """,
            countQuery = """
            select count(v) from Variante v join v.producto p join p.categoria c
            where (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:categoriaId is null or c.id = :categoriaId)
              and (:incluirInactivas = true or v.activo = true)
              and (:estado is null
                   or (:estado = 'AGOTADO'
                       and v.stockFisico - v.stockReservado <= 0)
                   or (:estado = 'BAJO'
                       and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado <= coalesce(v.puntoReorden, v.stockMinimo))
                   or (:estado = 'OK'
                       and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado > coalesce(v.puntoReorden, v.stockMinimo)))
            """)
    Page<Variante> consultar(@Param("patron") String patron, @Param("categoriaId") Long categoriaId,
            @Param("estado") String estado, @Param("incluirInactivas") boolean incluirInactivas,
            Pageable pageable);

    /** Versión tipada de {@link #consultar}: Hibernate no infiere el tipo de un enum nulo en {@code :estado is null}. */
    default Page<Variante> buscar(String patron, Long categoriaId, EstadoStock estado, boolean incluirInactivas,
            Pageable pageable) {
        return consultar(patron, categoriaId, estado != null ? estado.name() : null, incluirInactivas, pageable);
    }
}
