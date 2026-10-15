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
