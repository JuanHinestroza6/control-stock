package co.livorno.controlstock.inventario.repositorio;

import co.livorno.controlstock.inventario.modelo.Alerta;
import co.livorno.controlstock.inventario.modelo.EstadoAlerta;
import co.livorno.controlstock.inventario.modelo.TipoAlerta;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

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
