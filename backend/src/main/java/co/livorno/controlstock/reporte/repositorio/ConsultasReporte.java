package co.livorno.controlstock.reporte.repositorio;

import co.livorno.controlstock.reporte.dto.AgrupacionReporte;
import co.livorno.controlstock.reporte.dto.PeriodoLibro;
import co.livorno.controlstock.reporte.dto.VentaAnuladaItem;
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

    /** Acumulado de valorización de un grupo (categoría, producto o variante). */
    public record AcumuladoValorizacion(Long id, String codigo, String nombre, String categoria, long fisicas,
            long reservadas, BigDecimal valorCosto, BigDecimal valorVenta, long enCamino,
            BigDecimal valorEnCamino) {}

    /** Ventas REGISTRADA de un periodo del libro (por fecha de venta). */
    public record VentasPeriodo(String periodo, long numeroVentas, long unidades, BigDecimal ventasLista,
            BigDecimal descuentos, BigDecimal facturado, BigDecimal costo) {}

    /** Devoluciones de un periodo del libro (por fecha de la devolución). */
    public record DevolucionesPeriodo(String periodo, long unidades, BigDecimal valor, BigDecimal costo,
            BigDecimal mermaNoApta) {}

    /** Línea vendida (detalle del libro, solo Excel). */
    public record LineaDetalle(Long ventaId, LocalDateTime fecha, String sku, String producto, int cantidad,
            BigDecimal precioLista, BigDecimal precioUnitario, BigDecimal costoUnitario) {}

    /** Devolución del periodo (detalle del libro, solo Excel); sin el motivo, que es texto libre. */
    public record DevolucionDetalle(LocalDateTime fecha, Long ventaId, String sku, String producto, int cantidad,
            boolean aptaParaVenta, BigDecimal precioUnitario, BigDecimal costoUnitario) {}

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

    private static String formatoPeriodo(PeriodoLibro agrupacion, String columna) {
        return agrupacion == PeriodoLibro.MES ? "DATE_FORMAT(" + columna + ", '%Y-%m')"
                : "DATE_FORMAT(" + columna + ", '%Y-%m-%d')";
    }

    /** Ventas REGISTRADA por periodo (día o mes) de su fecha de venta. Solo periodos con ventas. */
    public List<VentasPeriodo> ventasPorPeriodo(LocalDate desde, LocalDate hasta, PeriodoLibro agrupacion) {
        String periodo = formatoPeriodo(agrupacion, "v.fecha");
        String sql = "SELECT " + periodo + " AS periodo, COUNT(DISTINCT v.id) AS numero_ventas,"
                + " SUM(d.cantidad) AS unidades, SUM(d.cantidad * d.precio_lista) AS ventas_lista,"
                + " SUM(d.cantidad * (d.precio_lista - d.precio_unitario)) AS descuentos,"
                + " SUM(d.cantidad * d.precio_unitario) AS facturado,"
                + " SUM(d.cantidad * d.costo_unitario) AS costo"
                + " FROM venta v JOIN detalle_venta d ON d.venta_id = v.id"
                + " WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta"
                + " GROUP BY " + periodo + " ORDER BY periodo";
        return jdbc.query(sql, limites(desde, hasta), (rs, n) -> new VentasPeriodo(rs.getString("periodo"),
                rs.getLong("numero_ventas"), rs.getBigDecimal("unidades").longValue(),
                rs.getBigDecimal("ventas_lista"), rs.getBigDecimal("descuentos"), rs.getBigDecimal("facturado"),
                rs.getBigDecimal("costo")));
    }

    /**
     * Devoluciones por periodo de la fecha de la devolución, aunque la venta sea anterior al rango. Las de ventas
     * ANULADAS no cuentan (no debería haberlas).
     */
    public List<DevolucionesPeriodo> devolucionesPorPeriodo(LocalDate desde, LocalDate hasta,
            PeriodoLibro agrupacion) {
        String periodo = formatoPeriodo(agrupacion, "dev.fecha");
        String sql = "SELECT " + periodo + " AS periodo, SUM(dev.cantidad) AS unidades,"
                + " SUM(dev.cantidad * d.precio_unitario) AS valor, SUM(dev.cantidad * d.costo_unitario) AS costo,"
                + " SUM(CASE WHEN dev.apta_para_venta THEN 0 ELSE dev.cantidad * d.costo_unitario END) AS merma"
                + " FROM devolucion dev JOIN detalle_venta d ON d.id = dev.detalle_venta_id"
                + " JOIN venta v ON v.id = d.venta_id"
                + " WHERE v.estado = 'REGISTRADA' AND dev.fecha >= :desde AND dev.fecha < :hasta"
                + " GROUP BY " + periodo + " ORDER BY periodo";
        return jdbc.query(sql, limites(desde, hasta), (rs, n) -> new DevolucionesPeriodo(rs.getString("periodo"),
                rs.getBigDecimal("unidades").longValue(), rs.getBigDecimal("valor"), rs.getBigDecimal("costo"),
                rs.getBigDecimal("merma")));
    }

    /** Ventas ANULADAS con fecha de venta en el rango, con el nombre de quien las anuló. */
    public List<VentaAnuladaItem> ventasAnuladas(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT v.id, v.fecha, v.total, v.anulada_en, u.nombre AS anulada_por, v.motivo_anulacion"
                + " FROM venta v LEFT JOIN usuario u ON u.id = v.anulada_por"
                + " WHERE v.estado = 'ANULADA' AND v.fecha >= :desde AND v.fecha < :hasta ORDER BY v.fecha, v.id";
        return jdbc.query(sql, limites(desde, hasta), (rs, n) -> new VentaAnuladaItem(rs.getLong("id"),
                String.format("V-%06d", rs.getLong("id")), rs.getObject("fecha", LocalDateTime.class),
                rs.getBigDecimal("total"), rs.getObject("anulada_en", LocalDateTime.class),
                rs.getString("anulada_por"), rs.getString("motivo_anulacion")));
    }

    /** Número de líneas de venta (ventas REGISTRADA) del rango: tamaño del detalle del libro. */
    public long contarLineasVenta(LocalDate desde, LocalDate hasta) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM venta v JOIN detalle_venta d ON d.venta_id = v.id"
                + " WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta",
                limites(desde, hasta), Long.class);
        return total == null ? 0 : total;
    }

    /** Una fila por línea vendida (ventas REGISTRADA), en orden de venta. */
    public List<LineaDetalle> detalleVentas(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT v.id AS venta_id, v.fecha, va.sku, p.nombre AS producto, d.cantidad, d.precio_lista,"
                + " d.precio_unitario, d.costo_unitario"
                + " FROM venta v JOIN detalle_venta d ON d.venta_id = v.id"
                + " JOIN variante va ON va.id = d.variante_id JOIN producto p ON p.id = va.producto_id"
                + " WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta"
                + " ORDER BY v.fecha, v.id, d.id";
        return jdbc.query(sql, limites(desde, hasta), (rs, n) -> new LineaDetalle(rs.getLong("venta_id"),
                rs.getObject("fecha", LocalDateTime.class), rs.getString("sku"), rs.getString("producto"),
                rs.getInt("cantidad"), rs.getBigDecimal("precio_lista"), rs.getBigDecimal("precio_unitario"),
                rs.getBigDecimal("costo_unitario")));
    }

    /** Una fila por devolución del rango (por fecha de la devolución), en orden cronológico. */
    public List<DevolucionDetalle> detalleDevoluciones(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT dev.fecha, v.id AS venta_id, va.sku, p.nombre AS producto, dev.cantidad,"
                + " dev.apta_para_venta, d.precio_unitario, d.costo_unitario"
                + " FROM devolucion dev JOIN detalle_venta d ON d.id = dev.detalle_venta_id"
                + " JOIN venta v ON v.id = d.venta_id JOIN variante va ON va.id = d.variante_id"
                + " JOIN producto p ON p.id = va.producto_id"
                + " WHERE v.estado = 'REGISTRADA' AND dev.fecha >= :desde AND dev.fecha < :hasta"
                + " ORDER BY dev.fecha, dev.id";
        return jdbc.query(sql, limites(desde, hasta), (rs, n) -> new DevolucionDetalle(
                rs.getObject("fecha", LocalDateTime.class), rs.getLong("venta_id"), rs.getString("sku"),
                rs.getString("producto"), rs.getInt("cantidad"), rs.getBoolean("apta_para_venta"),
                rs.getBigDecimal("precio_unitario"), rs.getBigDecimal("costo_unitario")));
    }

    /**
     * Valorización por grupo al momento de la consulta: físico (incluye lo reservado) por el precio de compra y de
     * venta actuales del producto, más lo en camino (planes APROBADO, cantidad aprobada por costo estimado), que es
     * informativo. Entran las variantes con físico &gt; 0 o con algo en camino; sin inactivas si
     * {@code incluirInactivas} es false (variante o producto inactivo).
     */
    public List<AcumuladoValorizacion> valorizacion(Long categoriaId, AgrupacionReporte agrupacion,
            boolean incluirInactivas) {
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
                + " SUM(va.stock_fisico) AS fisicas, SUM(va.stock_reservado) AS reservadas,"
                + " SUM(va.stock_fisico * p.precio_compra) AS valor_costo,"
                + " SUM(va.stock_fisico * p.precio_venta) AS valor_venta,"
                + " COALESCE(SUM(t.unidades), 0) AS en_camino, COALESCE(SUM(t.valor), 0) AS valor_en_camino"
                + " FROM variante va JOIN producto p ON p.id = va.producto_id"
                + " JOIN categoria c ON c.id = p.categoria_id" + CAMINO
                + " WHERE (va.stock_fisico > 0 OR t.unidades > 0)" + filtrosValorizacion(categoriaId, incluirInactivas)
                + " GROUP BY " + grupo;
        return jdbc.query(sql, parametrosValorizacion(categoriaId), (rs, n) -> new AcumuladoValorizacion(
                rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("categoria"),
                rs.getBigDecimal("fisicas").longValue(), rs.getBigDecimal("reservadas").longValue(),
                rs.getBigDecimal("valor_costo"), rs.getBigDecimal("valor_venta"),
                rs.getBigDecimal("en_camino").longValue(), rs.getBigDecimal("valor_en_camino")));
    }

    /** Productos distintos con existencias y precio de compra 0 (su valor a costo es 0), con los mismos filtros. */
    public long productosConCostoCero(Long categoriaId, boolean incluirInactivas) {
        String sql = "SELECT COUNT(DISTINCT p.id) FROM variante va JOIN producto p ON p.id = va.producto_id"
                + " WHERE va.stock_fisico > 0 AND p.precio_compra = 0"
                + filtrosValorizacion(categoriaId, incluirInactivas);
        Long n = jdbc.queryForObject(sql, parametrosValorizacion(categoriaId), Long.class);
        return n == null ? 0 : n;
    }

    private static final String CAMINO = " LEFT JOIN (SELECT dp.variante_id AS variante_id,"
            + " SUM(dp.cantidad_aprobada) AS unidades, SUM(dp.cantidad_aprobada * dp.costo_estimado) AS valor"
            + " FROM detalle_plan dp JOIN plan_compra pc ON pc.id = dp.plan_id WHERE pc.estado = 'APROBADO'"
            + " GROUP BY dp.variante_id) t ON t.variante_id = va.id";

    private static String filtrosValorizacion(Long categoriaId, boolean incluirInactivas) {
        return (incluirInactivas ? "" : " AND va.activo = TRUE AND p.activo = TRUE")
                + (categoriaId != null ? " AND p.categoria_id = :categoriaId" : "");
    }

    private static MapSqlParameterSource parametrosValorizacion(Long categoriaId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (categoriaId != null) {
            p.addValue("categoriaId", categoriaId);
        }
        return p;
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
