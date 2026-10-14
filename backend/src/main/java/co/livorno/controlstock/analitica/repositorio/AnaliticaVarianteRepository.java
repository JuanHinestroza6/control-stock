package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.modelo.AnaliticaVariante;
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
}
