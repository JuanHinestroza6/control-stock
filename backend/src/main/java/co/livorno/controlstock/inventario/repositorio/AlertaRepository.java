package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /** Alertas de variantes activas, con filtros opcionales; trae variante y producto sin N+1. */
    @Query(value = """
            select a from Alerta a join fetch a.variante v join fetch v.producto
            where v.activo = true and (:estado is null or a.estado = :estado) and (:tipo is null or a.tipo = :tipo)
            """, countQuery = """
            select count(a) from Alerta a
            where a.variante.activo = true and (:estado is null or a.estado = :estado)
              and (:tipo is null or a.tipo = :tipo)
            """)
    Page<Alerta> buscar(@Param("estado") EstadoAlerta estado, @Param("tipo") TipoAlerta tipo, Pageable pageable);

    /** Cuenta de alertas ACTIVAS de variantes activas por tipo. */
    @Query("""
            select a.tipo, count(a) from Alerta a
            where a.estado = co.livorno.controlstock.inventario.modelo.EstadoAlerta.ACTIVA
              and a.variante.activo = true
            group by a.tipo
            """)
    List<Object[]> contarActivasPorTipo();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alerta a where a.id = :id")
    Optional<Alerta> bloquear(@Param("id") Long id);

    Optional<Alerta> findByVarianteIdAndTipoAndEstado(Long varianteId, TipoAlerta tipo, EstadoAlerta estado);

    /**
     * Crea la alerta ACTIVA o, si ya existe una activa del mismo tipo para la variante (índice único), solo actualiza
     * {@code revisada_en}. Atómico: una carrera entre dos transacciones no produce excepción ni duplicado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO alerta (variante_id, tipo, estado, creada_en, revisada_en)
            VALUES (:varianteId, :tipo, 'ACTIVA', :ahora, :ahora)
            ON DUPLICATE KEY UPDATE revisada_en = :ahora
            """, nativeQuery = true)
    int abrirOActualizar(@Param("varianteId") Long varianteId, @Param("tipo") String tipo,
            @Param("ahora") LocalDateTime ahora);

    /** Cierra automáticamente (atendida_por null) la alerta activa del tipo, si existe. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Alerta a set a.estado = co.livorno.controlstock.inventario.modelo.EstadoAlerta.ATENDIDA,
                a.atendidaEn = :ahora, a.atendidaPor = null
            where a.variante.id = :varianteId and a.tipo = :tipo
              and a.estado = co.livorno.controlstock.inventario.modelo.EstadoAlerta.ACTIVA
            """)
    int cerrarActiva(@Param("varianteId") Long varianteId, @Param("tipo") TipoAlerta tipo,
            @Param("ahora") LocalDateTime ahora);
}
