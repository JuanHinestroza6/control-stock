package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.dto.AgrupacionRanking;
import co.livorno.controlstock.analitica.dto.CriterioRanking;
import co.livorno.controlstock.analitica.dto.FiltroMasVendidos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelo de lectura de la analítica sobre ventas y devoluciones (ADR-009, R8): consultas SQL agregadas, solo lectura.
 * Los rangos son {@code [desde, hasta)} por día calendario; las fechas se guardan ya en hora de America/Bogota
 * (LocalDateTime), por lo que los límites son medianoche local sin conversión.
 *
 * <p>Índices: {@code idx_venta_estado_fecha} (estado, fecha) acota las ventas; {@code detalle_venta.venta_id} (FK) y
 * {@code idx_devolucion_detalle_venta} resuelven los joins; {@code idx_devolucion_fecha} acota las devoluciones.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasVentaAnalitica {

    private static final String INGRESO_NETO = """
            SELECT linea.variante_id AS variante_id,
                   SUM(linea.cantidad - linea.devuelta) AS unidades,
                   SUM((linea.cantidad - linea.devuelta) * linea.precio_unitario) AS ingreso
            FROM (
                SELECT d.variante_id, d.cantidad, d.precio_unitario,
                       (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                        WHERE dev.detalle_venta_id = d.id) AS devuelta
                FROM venta v
                JOIN detalle_venta d ON d.venta_id = v.id
                WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
            ) linea
            GROUP BY linea.variante_id
            ORDER BY linea.variante_id
            """;

    private static final String DEMANDA_DIARIA = """
            SELECT variante_id, dia, SUM(neto) AS neto
            FROM (
                SELECT d.variante_id AS variante_id, DATE(v.fecha) AS dia, d.cantidad AS neto
                FROM venta v
                JOIN detalle_venta d ON d.venta_id = v.id
                WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
                UNION ALL
                SELECT d.variante_id, DATE(dev.fecha), -dev.cantidad
                FROM devolucion dev
                JOIN detalle_venta d ON d.id = dev.detalle_venta_id
                JOIN venta v ON v.id = d.venta_id
                WHERE dev.apta_para_venta = TRUE AND v.estado = 'REGISTRADA'
                  AND dev.fecha >= :desde AND dev.fecha < :hasta
            ) mov
            GROUP BY variante_id, dia
            HAVING SUM(neto) <> 0
            ORDER BY variante_id, dia
            """;

    /** Líneas vendidas con su devuelta total (demanda comercial); misma semántica que {@link #INGRESO_NETO}. */
    private static final String LINEAS_NETAS = """
            SELECT linea.variante_id AS variante_id, linea.cantidad - linea.devuelta AS unidades,
                   (linea.cantidad - linea.devuelta) * linea.precio_unitario AS ingreso
            FROM (
                SELECT d.variante_id, d.cantidad, d.precio_unitario,
                       (SELECT COALESCE(SUM(dev.cantidad), 0) FROM devolucion dev
                        WHERE dev.detalle_venta_id = d.id) AS devuelta
                FROM venta v
                JOIN detalle_venta d ON d.venta_id = v.id
                WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
            ) linea
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasVentaAnalitica(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Demanda comercial (ABC, más vendidos): ventas REGISTRADA con fecha en {@code [desde, hasta)}; todas las
     * devoluciones restan, atribuidas a la fecha de la venta. Solo variantes con alguna venta en el rango.
     */
    public List<VentaNetaVariante> ingresoNetoPorVariante(LocalDate desde, LocalDate hasta) {
        return jdbc.query(INGRESO_NETO, limites(desde, hasta), (rs, n) -> new VentaNetaVariante(
                rs.getLong("variante_id"), rs.getLong("unidades"),
                rs.getBigDecimal("ingreso").setScale(2, RoundingMode.HALF_UP)));
    }

    /**
     * Demanda de inventario (ROP): por variante y día, vendidas (fecha de la venta, incluidas las convertidas de
     * reservas) − devueltas aptas (fecha de la devolución). Filas dispersas, sin los días con neto 0.
     */
    public List<DemandaDiaVariante> demandaDiariaNeta(LocalDate desde, LocalDate hasta) {
        return jdbc.query(DEMANDA_DIARIA, limites(desde, hasta), (rs, n) -> new DemandaDiaVariante(
                rs.getLong("variante_id"), rs.getObject("dia", LocalDate.class),
                new BigDecimal(rs.getString("neto")).intValueExact()));
    }

    /**
     * Ranking de más vendidos (R10): demanda comercial en {@code [desde, hasta]} inclusivo, categoría opcional, top-N.
     * Desempata por el otro criterio y luego por id; excluye los ítems con neto 0.
     */
    public List<MasVendidoFila> masVendidos(FiltroMasVendidos f) {
        boolean porVariante = f.agrupacion() == AgrupacionRanking.VARIANTE;
        String columnasVariante = porVariante
                ? "va.id AS variante_id, va.sku AS sku, va.talla AS talla, va.color AS color, va.activo AS activo,"
                : "NULL AS variante_id, NULL AS sku, NULL AS talla, NULL AS color, p.activo AS activo,";
        String grupo = porVariante ? "va.id, va.sku, va.talla, va.color, va.activo, " : "p.activo, ";
        String orden = f.criterio() == CriterioRanking.UNIDADES
                ? "unidades DESC, ingreso DESC" : "ingreso DESC, unidades DESC";
        String idDesempate = porVariante ? "va.id" : "p.id";
        String sql = "SELECT p.id AS producto_id, p.codigo AS codigo, p.nombre AS nombre, c.id AS categoria_id, "
                + "c.nombre AS categoria, " + columnasVariante
                + " SUM(l.unidades) AS unidades, SUM(l.ingreso) AS ingreso"
                + " FROM (" + LINEAS_NETAS + ") l"
                + " JOIN variante va ON va.id = l.variante_id JOIN producto p ON p.id = va.producto_id"
                + " JOIN categoria c ON c.id = p.categoria_id"
                + (f.categoriaId() != null ? " WHERE c.id = :categoriaId" : "")
                + " GROUP BY " + grupo + "p.id, p.codigo, p.nombre, c.id, c.nombre"
                + " HAVING SUM(l.unidades) > 0"
                + " ORDER BY " + orden + ", " + idDesempate + " ASC LIMIT :limite";
        MapSqlParameterSource params = limitesInclusivos(f).addValue("limite", f.limite());
        return jdbc.query(sql, params, (rs, n) -> new MasVendidoFila(
                rs.getLong("producto_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getLong("categoria_id"), rs.getString("categoria"),
                porVariante ? rs.getLong("variante_id") : null, rs.getString("sku"), rs.getString("talla"),
                rs.getString("color"), rs.getBigDecimal("unidades").longValueExact(),
                rs.getBigDecimal("ingreso").setScale(2, RoundingMode.HALF_UP), rs.getBoolean("activo")));
    }

    /** Totales netos del mismo periodo y categoría que {@link #masVendidos} (no solo del top). */
    public TotalVentaNeta totalVentaNeta(FiltroMasVendidos f) {
        String sql = "SELECT COALESCE(SUM(l.unidades), 0) AS unidades, COALESCE(SUM(l.ingreso), 0) AS ingreso"
                + " FROM (" + LINEAS_NETAS + ") l"
                + (f.categoriaId() != null ? " JOIN variante va ON va.id = l.variante_id"
                        + " JOIN producto p ON p.id = va.producto_id WHERE p.categoria_id = :categoriaId" : "");
        return jdbc.queryForObject(sql, limitesInclusivos(f), (rs, n) -> new TotalVentaNeta(
                rs.getBigDecimal("unidades").longValueExact(),
                rs.getBigDecimal("ingreso").setScale(2, RoundingMode.HALF_UP)));
    }

    private static MapSqlParameterSource limitesInclusivos(FiltroMasVendidos f) {
        MapSqlParameterSource p = limites(f.desde(), f.hasta().plusDays(1));
        if (f.categoriaId() != null) {
            p.addValue("categoriaId", f.categoriaId());
        }
        return p;
    }

    private static MapSqlParameterSource limites(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El rango debe cumplir desde < hasta.");
        }
        Map<String, LocalDateTime> p = Map.of("desde", desde.atStartOfDay(), "hasta", hasta.atStartOfDay());
        return new MapSqlParameterSource(Map.copyOf(p));
    }
}
