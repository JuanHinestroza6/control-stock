package co.livorno.controlstock.analitica.repositorio;

import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serie mensual de demanda comercial (REQ-09): ventas REGISTRADA, todas las devoluciones restan y se atribuyen al mes
 * de la venta; igual que "Más vendidos". Rango {@code [primerMes, ultimoMes + 1)}. Índice {@code idx_venta_estado_fecha}.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasTendencias {

    private static final String BASE = """
            SELECT DATE_FORMAT(l.fecha, '%Y-%m') AS mes, SUM(l.unidades) AS unidades, SUM(l.ingreso) AS ingreso
            FROM (
                SELECT v.fecha AS fecha, d.variante_id AS variante_id,
                       d.cantidad - (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                                     WHERE dev.detalle_venta_id = d.id) AS unidades,
                       (d.cantidad - (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                                      WHERE dev.detalle_venta_id = d.id)) * d.precio_unitario AS ingreso
                FROM venta v
                JOIN detalle_venta d ON d.venta_id = v.id
                WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
            ) l
            JOIN variante va ON va.id = l.variante_id
            JOIN producto p ON p.id = va.producto_id
            WHERE 1 = 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasTendencias(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filas dispersas (solo meses con ventas), de {@code primerMes} a {@code ultimoMes} inclusive. */
    public List<FilaTendencia> porMes(YearMonth primerMes, YearMonth ultimoMes, Long categoriaId, Long productoId) {
        StringBuilder sql = new StringBuilder(BASE);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("desde", primerMes.atDay(1).atStartOfDay())
                .addValue("hasta", ultimoMes.plusMonths(1).atDay(1).atStartOfDay());
        if (categoriaId != null) {
            sql.append(" AND p.categoria_id = :categoriaId");
            params.addValue("categoriaId", categoriaId);
        }
        if (productoId != null) {
            sql.append(" AND p.id = :productoId");
            params.addValue("productoId", productoId);
        }
        sql.append(" GROUP BY DATE_FORMAT(l.fecha, '%Y-%m') ORDER BY mes");
        return jdbc.query(sql.toString(), params, (rs, n) -> new FilaTendencia(YearMonth.parse(rs.getString("mes")),
                rs.getBigDecimal("unidades").longValueExact(),
                rs.getBigDecimal("ingreso").setScale(2, RoundingMode.HALF_UP)));
    }
}
