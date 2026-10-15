package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
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

    /** Deja en null ROP y clase de las variantes inactivas o de productos inactivos. */
    @Modifying
    @Query(value = """
            UPDATE variante SET punto_reorden = NULL, clase_abc = NULL
            WHERE (activo = 0 OR producto_id IN (SELECT id FROM producto WHERE activo = 0))
              AND (punto_reorden IS NOT NULL OR clase_abc IS NOT NULL)""", nativeQuery = true)
    int limpiarInactivas();

    /**
     * Página de la clasificación ABC: solo variantes activas de productos activos. Filtros nulos se ignoran; la clase
     * llega como 'A', 'B' o 'C' y {@code patron} se construye con {@code ProductoRepository.patronContiene}.
     */
    @Query(value = """
            select a from AnaliticaVariante a join fetch a.variante v join fetch v.producto p join fetch p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or cast(a.claseAbc as string) = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
            """,
            countQuery = """
            select count(a) from AnaliticaVariante a join a.variante v join v.producto p join p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or cast(a.claseAbc as string) = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
            """)
    Page<AnaliticaVariante> consultarAbc(@Param("clase") String clase, @Param("categoriaId") Long categoriaId,
            @Param("patron") String patron, Pageable pageable);

    /**
     * Página del punto de reorden. {@code modoAlerta}: 0 = sin filtro, 1 = solo con disponible ≤ umbral efectivo
     * (ROP, o stock mínimo si no hay), 2 = solo con disponible > umbral efectivo.
     */
    @Query(value = """
            select a from AnaliticaVariante a join fetch a.variante v join fetch v.producto p join fetch p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or cast(a.claseAbc as string) = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:metodo is null or cast(a.metodoReorden as string) = :metodo)
              and (:modoAlerta = 0
                   or (:modoAlerta = 1 and v.stockFisico - v.stockReservado <= coalesce(a.puntoReorden, v.stockMinimo))
                   or (:modoAlerta = 2 and v.stockFisico - v.stockReservado > coalesce(a.puntoReorden, v.stockMinimo)))
            """,
            countQuery = """
            select count(a) from AnaliticaVariante a join a.variante v join v.producto p join p.categoria c
            where v.activo = true and p.activo = true
              and (:clase is null or cast(a.claseAbc as string) = :clase)
              and (:categoriaId is null or c.id = :categoriaId)
              and (:patron is null or v.sku like :patron escape '!' or p.codigo like :patron escape '!'
                   or p.nombre like :patron escape '!')
              and (:metodo is null or cast(a.metodoReorden as string) = :metodo)
              and (:modoAlerta = 0
                   or (:modoAlerta = 1 and v.stockFisico - v.stockReservado <= coalesce(a.puntoReorden, v.stockMinimo))
                   or (:modoAlerta = 2 and v.stockFisico - v.stockReservado > coalesce(a.puntoReorden, v.stockMinimo)))
            """)
    Page<AnaliticaVariante> consultarReorden(@Param("clase") String clase, @Param("categoriaId") Long categoriaId,
            @Param("patron") String patron, @Param("metodo") String metodo, @Param("modoAlerta") int modoAlerta,
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
}
