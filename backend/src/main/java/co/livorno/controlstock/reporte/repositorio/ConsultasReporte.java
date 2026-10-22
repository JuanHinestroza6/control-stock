package co.livorno.controlstock.reporte.repositorio;

import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelo de lectura de los reportes (ADR-011): consultas SQL agregadas, solo lectura. Los rangos de fechas llegan
 * inclusivos y se traducen a {@code [desde 00:00, hasta + 1 día 00:00)}; las fechas se guardan ya en hora de
 * America/Bogota. Solo ventas REGISTRADA. Nunca selecciona nombre ni contacto del cliente (Ley 1581).
 */
@Component
@Transactional(readOnly = true)
public class ConsultasReporte {

    /** Acumulado de rentabilidad de un grupo (categoría, producto o variante); sin redondear a escala de reporte. */
    public record AcumuladoRentabilidad(Long id, String codigo, String nombre, String categoria, long vendidas,
            long devueltas, BigDecimal ventasLista, BigDecimal descuentos, BigDecimal facturado,
            BigDecimal devoluciones, BigDecimal costoVentas, BigDecimal mermaNoApta, long lineasCostoCero) {}

    private static final String LINEAS_VENDIDAS = """
            SELECT d.variante_id AS variante_id, d.cantidad AS cantidad, d.precio_lista AS precio_lista,
                   d.precio_unitario AS precio_unitario, d.costo_unitario AS costo_unitario,
                   (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                    WHERE dev.detalle_venta_id = d.id) AS devuelta,
                   (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                    WHERE dev.detalle_venta_id = d.id AND dev.apta_para_venta = FALSE) AS devuelta_no_apta
            FROM venta v
            JOIN detalle_venta d ON d.venta_id = v.id
            WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasReporte(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Rentabilidad por grupo: ventas REGISTRADA con fecha en {@code [desde, hasta]}; todas las devoluciones de sus
     * líneas, de cualquier fecha, restan (atribuidas a la fecha de la venta). Categoría opcional.
     */
    public List<AcumuladoRentabilidad> rentabilidad(LocalDate desde, LocalDate hasta, Long categoriaId,
            AgrupacionReporte agrupacion) {
        String columnas;
        String grupo;
        switch (agrupacion) {
            case CATEGORIA -> {
                columnas = "c.id AS id, NULL AS codigo, c.nombre AS nombre, NULL AS categoria";
                grupo = "c.id, c.nombre";
            }
            case PRODUCTO -> {
                columnas = "p.id AS id, p.codigo AS codigo, p.nombre AS nombre, c.nombre AS categoria";
                grupo = "p.id, p.codigo, p.nombre, c.nombre";
            }
            default -> {
                columnas = "va.id AS id, va.sku AS codigo,"
                        + " CONCAT(p.nombre, ' (', va.talla, ' ', va.color, ')') AS nombre, c.nombre AS categoria";
                grupo = "va.id, va.sku, va.talla, va.color, p.nombre, c.nombre";
            }
        }
        String sql = "SELECT " + columnas + ","
                + " SUM(l.cantidad) AS vendidas, SUM(l.devuelta) AS devueltas,"
                + " SUM(l.cantidad * l.precio_lista) AS ventas_lista,"
                + " SUM(l.cantidad * (l.precio_lista - l.precio_unitario)) AS descuentos,"
                + " SUM(l.cantidad * l.precio_unitario) AS facturado,"
                + " SUM(l.devuelta * l.precio_unitario) AS devoluciones,"
                + " SUM((l.cantidad - l.devuelta) * l.costo_unitario) AS costo_ventas,"
                + " SUM(l.devuelta_no_apta * l.costo_unitario) AS merma_no_apta,"
                + " SUM(CASE WHEN l.costo_unitario = 0 THEN 1 ELSE 0 END) AS lineas_costo_cero"
                + " FROM (" + LINEAS_VENDIDAS + ") l"
                + " JOIN variante va ON va.id = l.variante_id JOIN producto p ON p.id = va.producto_id"
                + " JOIN categoria c ON c.id = p.categoria_id"
                + (categoriaId != null ? " WHERE c.id = :categoriaId" : "")
                + " GROUP BY " + grupo;
        MapSqlParameterSource params = limites(desde, hasta);
        if (categoriaId != null) {
            params.addValue("categoriaId", categoriaId);
        }
        return jdbc.query(sql, params, (rs, n) -> new AcumuladoRentabilidad(rs.getLong("id"),
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("categoria"),
                rs.getBigDecimal("vendidas").longValue(), rs.getBigDecimal("devueltas").longValue(),
                rs.getBigDecimal("ventas_lista"), rs.getBigDecimal("descuentos"), rs.getBigDecimal("facturado"),
                rs.getBigDecimal("devoluciones"), rs.getBigDecimal("costo_ventas"),
                rs.getBigDecimal("merma_no_apta"), rs.getBigDecimal("lineas_costo_cero").longValue()));
    }

    /** Nombre de la categoría, o null si no existe. */
    public String nombreCategoria(Long categoriaId) {
        List<String> r = jdbc.query("SELECT nombre FROM categoria WHERE id = :id",
                new MapSqlParameterSource("id", categoriaId), (rs, n) -> rs.getString("nombre"));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Límites {@code [desde 00:00, hasta + 1 día 00:00)} para un rango inclusivo. */
    static MapSqlParameterSource limites(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango debe cumplir desde <= hasta.");
        }
        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        return new MapSqlParameterSource().addValue("desde", ini).addValue("hasta", fin);
    }
}
