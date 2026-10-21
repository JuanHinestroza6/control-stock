package co.livorno.controlstock.analitica.repositorio;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelo de lectura para la metrica de alertas de la ejecucion analitica (ADR-009, D4): conteos SQL directos sobre
 * variantes y alertas. Solo lectura.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasEstadoAlertas {

    private final JdbcTemplate jdbc;

    public ConsultasEstadoAlertas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ids de todas las variantes (activas o no: las inactivas cierran sus alertas), en orden de id. */
    public List<Long> idsTodasLasVariantes() {
        return jdbc.queryForList("SELECT id FROM variante ORDER BY id", Long.class);
    }

    /** Variantes activas de productos activos. */
    public int variantesActivas() {
        return contar("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id "
                + "WHERE v.activo = TRUE AND p.activo = TRUE");
    }

    /** Variantes activas de productos activos con una alerta ACTIVA AGOTADO o STOCK_BAJO. */
    public int enAlertaStock() {
        return contar(alertasActivas("('AGOTADO', 'STOCK_BAJO')"));
    }

    /** Variantes activas de productos activos con una alerta ACTIVA SIN_ROTACION. */
    public int sinRotacionActivas() {
        return contar(alertasActivas("('SIN_ROTACION')"));
    }

    /** Ids de variantes activas de productos activos. */
    public java.util.Set<Long> idsVariantesActivas() {
        return new java.util.HashSet<>(jdbc.queryForList("SELECT v.id FROM variante v JOIN producto p "
                + "ON p.id = v.producto_id WHERE v.activo = TRUE AND p.activo = TRUE", Long.class));
    }

    /**
     * Contrafactual (ADR-010): variantes activas que estarían en AGOTADO o STOCK_BAJO si no se contara lo que viene en
     * camino. Las que tienen SIN_ROTACION activa no cuentan (prioridad AGOTADO &gt; SIN_ROTACION &gt; STOCK_BAJO; una
     * SIN_ROTACION implica disponible &gt; 0, así que nunca sería AGOTADO).
     */
    public int enAlertaSinTransito() {
        return contar("SELECT COUNT(*) FROM variante v JOIN producto p ON p.id = v.producto_id "
                + "WHERE v.activo = TRUE AND p.activo = TRUE "
                + "AND (v.stock_fisico - v.stock_reservado <= 0 "
                + "OR v.stock_fisico - v.stock_reservado <= COALESCE(v.punto_reorden, v.stock_minimo)) "
                + "AND NOT EXISTS (SELECT 1 FROM alerta a WHERE a.variante_id = v.id AND a.estado = 'ACTIVA' "
                + "AND a.tipo = 'SIN_ROTACION')");
    }

    private static String alertasActivas(String tipos) {
        return "SELECT COUNT(DISTINCT v.id) FROM alerta a "
                + "JOIN variante v ON v.id = a.variante_id JOIN producto p ON p.id = v.producto_id "
                + "WHERE a.estado = 'ACTIVA' AND a.tipo IN " + tipos + " AND v.activo = TRUE AND p.activo = TRUE";
    }

    private int contar(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
