package co.livorno.controlstock.analitica.repositorio;

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

    private static MapSqlParameterSource limites(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El rango debe cumplir desde < hasta.");
        }
        Map<String, LocalDateTime> p = Map.of("desde", desde.atStartOfDay(), "hasta", hasta.atStartOfDay());
        return new MapSqlParameterSource(Map.copyOf(p));
    }
}
