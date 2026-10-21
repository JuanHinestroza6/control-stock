package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
import co.livorno.controlstock.analitica.modelo.EnCaminoVariante;
import co.livorno.controlstock.analitica.modelo.MetodoReorden;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Unico lugar que escribe {@code variante.punto_reorden} y {@code variante.clase_abc} (ADR-009): una sentencia
 * dirigida que no toca {@code version} ni el stock, asi el calculo nocturno no provoca 409 a las ventas. Una regla
 * ArchUnit impide que otro paquete tenga un {@code @Modifying} sobre esas columnas.
 */
public interface AnaliticaVarianteRepository extends JpaRepository<AnaliticaVariante, Long> {

    /** Fija ROP y clase de una variante; {@code clase} es 'A', 'B', 'C' o null. Devuelve las filas afectadas. */
    @Modifying
    @Query(value = "UPDATE variante SET punto_reorden = :rop, clase_abc = :clase WHERE id = :id", nativeQuery = true)
    int aplicarAVariante(@Param("id") Long id, @Param("rop") Integer rop, @Param("clase") String clase);

    /**
     * Bloquea la fila de la variante solo si nadie la tiene bloqueada: no espera ni se encola. Una venta en curso
     * (que ya tiene S sobre la variante por la FK del movimiento y pedirá X al confirmar) nunca queda detrás de una
     * solicitud nuestra, así que no hay interbloqueo S-X (ADR-009, R1). Vacío = ocupada (o inexistente).
     */
    @Query(value = "SELECT id FROM variante WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<Long> bloquearSiLibre(@Param("id") Long id);

    /**
     * Página de la clasificación ABC: solo variantes activas de productos activos. Filtros nulos se ignoran; la clase
     * llega como 'A', 'B' o 'C' y {@code patron} se construye con {@code ProductoRepository.patronContiene}.
     */
    @Query(value = """
            select a from AnaliticaVariante a join fetch a.variante v join fetch v.producto p join fetch p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or a.claseAbc = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
            """,
            countQuery = """
            select count(a) from AnaliticaVariante a join a.variante v join v.producto p join p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or a.claseAbc = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
            """)
    Page<AnaliticaVariante> consultarAbc(@Param("clase") Character clase, @Param("categoriaId") Long categoriaId,
            @Param("patron") String patron, Pageable pageable);

    /**
     * Página del punto de reorden. {@code modoAlerta}: 0 = sin filtro, 1 = en alerta (AGOTADO: disponible 0; STOCK_BAJO:
     * posición = disponible + en camino ≤ umbral efectivo, ROP o stock mínimo si no hay), 2 = sin alerta de stock.
     * El en camino sale de {@link EnCaminoVariante} (planes APROBADO, ADR-010).
     */
    @Query(value = """
            select a from AnaliticaVariante a join fetch a.variante v join fetch v.producto p join fetch p.categoria c
            left join EnCaminoVariante e on e.varianteId = v.id
            where v.activo = true and p.activo = true
              and (:clase is null or a.claseAbc = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:metodo is null or a.metodoReorden = :metodo)
              and (:modoAlerta = 0
                   or (:modoAlerta = 1 and (v.stockFisico - v.stockReservado = 0
                        or v.stockFisico - v.stockReservado + coalesce(e.cantidad, 0L)
                           <= coalesce(a.puntoReorden, v.stockMinimo)))
                   or (:modoAlerta = 2 and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado + coalesce(e.cantidad, 0L)
                           > coalesce(a.puntoReorden, v.stockMinimo)))
            """,
            countQuery = """
            select count(a) from AnaliticaVariante a join a.variante v join v.producto p join p.categoria c
            left join EnCaminoVariante e on e.varianteId = v.id
            where v.activo = true and p.activo = true
              and (:clase is null or a.claseAbc = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:metodo is null or a.metodoReorden = :metodo)
              and (:modoAlerta = 0
                   or (:modoAlerta = 1 and (v.stockFisico - v.stockReservado = 0
                        or v.stockFisico - v.stockReservado + coalesce(e.cantidad, 0L)
                           <= coalesce(a.puntoReorden, v.stockMinimo)))
                   or (:modoAlerta = 2 and v.stockFisico - v.stockReservado > 0
                       and v.stockFisico - v.stockReservado + coalesce(e.cantidad, 0L)
                           > coalesce(a.puntoReorden, v.stockMinimo)))
            """)
    Page<AnaliticaVariante> consultarReorden(@Param("clase") Character clase, @Param("categoriaId") Long categoriaId,
            @Param("patron") String patron, @Param("metodo") MetodoReorden metodo, @Param("modoAlerta") int modoAlerta,
            Pageable pageable);

    /** Variantes e ingreso neto por clase (agregado en SQL), solo variantes activas de productos activos. */
    @Query("""
            select new co.livorno.controlstock.analitica.repositorio.ResumenClaseFila(
                cast(a.claseAbc as string), count(a), coalesce(sum(a.ingresoNeto), 0))
            from AnaliticaVariante a join a.variante v join v.producto p
            where v.activo = true and p.activo = true
            group by a.claseAbc
            """)
    List<ResumenClaseFila> resumenPorClase();

    /**
     * Universo de la sugerencia de compra: variantes activas de productos activos (categoría opcional), por id, con
     * la demanda media histórica de la última instantánea si existe.
     */
    @Query("""
            select new co.livorno.controlstock.analitica.repositorio.VarianteSugerible(
                v.id, v.puntoReorden, v.stockMinimo, v.stockFisico - v.stockReservado, p.leadTimeDias, p.loteMinimo,
                a.demandaMedia, v.claseAbc)
            from Variante v join v.producto p left join AnaliticaVariante a on a.varianteId = v.id
            where v.activo = true and p.activo = true
              and (:categoriaId is null or p.categoria.id = :categoriaId)
            order by v.id
            """)
    List<VarianteSugerible> sugeribles(@Param("categoriaId") Long categoriaId);
}
