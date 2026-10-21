package co.livorno.controlstock.analitica.repositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demanda de inventario mensual para el pronostico (ADR-010, decision 1), solo lectura y una consulta por lote.
 * Neto = vendidas (fecha de la venta, ventas REGISTRADA, incluidas las convertidas de reservas) - devueltas aptas
 * (fecha de la devolucion, de ventas REGISTRADA). Las fechas ya estan en America/Bogota, sin conversion.
 *
 * <p>Indices: {@code idx_venta_estado_fecha} acota las ventas, {@code idx_devolucion_fecha} las devoluciones.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasDemandaMensual {

    private static final String MOVIMIENTOS = """
            SELECT d.variante_id AS variante_id, v.fecha AS fecha, d.cantidad AS neto
            FROM venta v
            JOIN detalle_venta d ON d.venta_id = v.id
            WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta
            UNION ALL
            SELECT d.variante_id, dev.fecha, -dev.cantidad
            FROM devolucion dev
            JOIN detalle_venta d ON d.id = dev.detalle_venta_id
            JOIN venta v ON v.id = d.venta_id
            WHERE dev.apta_para_venta = TRUE AND v.estado = 'REGISTRADA'
              AND dev.fecha >= :desde AND dev.fecha < :hasta
            """;

    private static final String POR_PRODUCTO_Y_MES = """
            SELECT va.producto_id AS producto_id, DATE_FORMAT(m.fecha, '%Y-%m') AS mes, SUM(m.neto) AS neto
            FROM (""" + MOVIMIENTOS + """
            ) m
            JOIN variante va ON va.id = m.variante_id
            GROUP BY va.producto_id, DATE_FORMAT(m.fecha, '%Y-%m')
            HAVING SUM(m.neto) > 0
            ORDER BY va.producto_id, mes
            """;

    private static final String POR_VARIANTE = """
            SELECT m.variante_id AS variante_id, SUM(m.neto) AS neto
            FROM (""" + MOVIMIENTOS + """
            ) m
            GROUP BY m.variante_id
            ORDER BY m.variante_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ConsultasDemandaMensual(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Mes en curso segun el reloj de la aplicacion (America/Bogota): el primero que queda fuera de la serie. */
    public YearMonth mesActual() {
        return YearMonth.now(clock);
    }

    /**
     * Neto mensual por producto de los {@code maxMeses} meses completos anteriores a {@code mesActual}
     * ({@code [mesActual - maxMeses, mesActual)}); el mes en curso nunca entra. El neto de cada mes se recorta a 0 (se
     * omiten los meses con neto &lt;= 0). Los productos creados a mitad de periodo los acota luego {@code SerieMensual}.
     */
    public List<DemandaMensualProducto> netoPorProductoYMes(YearMonth mesActual, int maxMeses) {
        return jdbc.query(POR_PRODUCTO_Y_MES, limites(mesActual, maxMeses), (rs, n) -> new DemandaMensualProducto(
                rs.getLong("producto_id"), YearMonth.parse(rs.getString("mes")),
                rs.getBigDecimal("neto").intValueExact()));
    }

    /** Igual que {@link #netoPorProductoYMes(YearMonth, int)} con el mes actual del {@code Clock}. */
    public List<DemandaMensualProducto> netoPorProductoYMes(int maxMeses) {
        return netoPorProductoYMes(mesActual(), maxMeses);
    }

    /**
     * Neto total por variante en {@code [desde, hasta)} (sin recorte: puede ser negativo, el consumidor lo trata como
     * 0). Sirve de peso de la disponibilidad ponderada (serie completa) y de participacion en la ventana de mezcla de
     * 180 dias. Solo aparecen las variantes con movimiento de demanda en el rango.
     */
    public Map<Long, Long> netoPorVariante(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El rango debe cumplir desde < hasta.");
        }
        Map<Long, Long> neto = new HashMap<>();
        jdbc.query(POR_VARIANTE, rango(desde.atStartOfDay(), hasta.atStartOfDay()), rs -> {
            neto.put(rs.getLong("variante_id"), rs.getBigDecimal("neto").longValueExact());
        });
        return neto;
    }

    private static MapSqlParameterSource limites(YearMonth mesActual, int maxMeses) {
        if (mesActual == null || maxMeses < 1) {
            throw new IllegalArgumentException("Se requiere el mes actual y al menos 1 mes de historia.");
        }
        return rango(mesActual.minusMonths(maxMeses).atDay(1).atStartOfDay(), mesActual.atDay(1).atStartOfDay());
    }

    private static MapSqlParameterSource rango(LocalDateTime desde, LocalDateTime hasta) {
        return new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);
    }
}
