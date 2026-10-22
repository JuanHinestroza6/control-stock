package co.livorno.controlstock.reporte.repositorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelo de lectura del panel de inicio (ADR-011): conteos y sumas agregadas, solo lectura. Nunca selecciona nombre ni
 * contacto del cliente (Ley 1581). Solo ventas REGISTRADA.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasPanel {

    /** Ventas de un día: ingreso neto de devoluciones y número de ventas. */
    public record VentasDia(LocalDate fecha, long numeroVentas, BigDecimal ingresoNeto) {}

    /** Ventas de un rango: ingreso neto, costo de ventas (neto de devoluciones) y número de ventas. */
    public record VentasRango(long numeroVentas, BigDecimal ingresoNeto, BigDecimal costoVentas) {}

    /** Existencias de variantes activas de productos activos. */
    public record Existencias(long variantes, long fisicas, long disponibles) {}

    /** Alertas ACTIVAS por tipo. */
    public record AlertasActivas(long agotado, long stockBajo, long sinRotacion) {}

    /** Apartados ACTIVOS. */
    public record ReservasActivas(long activas, long unidades, long porVencer) {}

    /** Planes de compra: borradores, aprobados en camino, sus unidades y valor, y los atrasados. */
    public record Compras(long borradores, long enCamino, long unidadesEnCamino, BigDecimal valorEnCamino,
            long atrasados) {}

    private static final String NETO = "(d.cantidad - COALESCE((SELECT SUM(dev.cantidad) FROM devolucion dev"
            + " WHERE dev.detalle_venta_id = d.id), 0))";

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasPanel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ventas por día en {@code [desde, hasta]} inclusivo; solo días con ventas. */
    public List<VentasDia> ventasPorDia(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT DATE(v.fecha) AS dia, COUNT(DISTINCT v.id) AS numero_ventas,"
                + " SUM(" + NETO + " * d.precio_unitario) AS neto"
                + " FROM venta v JOIN detalle_venta d ON d.venta_id = v.id"
                + " WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta"
                + " GROUP BY DATE(v.fecha) ORDER BY dia";
        return jdbc.query(sql, ConsultasReporte.limites(desde, hasta), (rs, n) -> new VentasDia(
                rs.getObject("dia", LocalDate.class), rs.getLong("numero_ventas"), rs.getBigDecimal("neto")));
    }

    /** Totales de ventas en {@code [desde, hasta]} inclusivo. */
    public VentasRango ventasRango(LocalDate desde, LocalDate hasta) {
        String sql = "SELECT COUNT(DISTINCT v.id) AS numero_ventas,"
                + " SUM(" + NETO + " * d.precio_unitario) AS neto,"
                + " SUM(" + NETO + " * d.costo_unitario) AS costo"
                + " FROM venta v JOIN detalle_venta d ON d.venta_id = v.id"
                + " WHERE v.estado = 'REGISTRADA' AND v.fecha >= :desde AND v.fecha < :hasta";
        return jdbc.queryForObject(sql, ConsultasReporte.limites(desde, hasta), (rs, n) -> new VentasRango(
                rs.getLong("numero_ventas"), cero(rs.getBigDecimal("neto")), cero(rs.getBigDecimal("costo"))));
    }

    /** Existencias del catálogo activo (variante y producto activos). */
    public Existencias existencias() {
        String sql = "SELECT COUNT(*) AS variantes, COALESCE(SUM(va.stock_fisico), 0) AS fisicas,"
                + " COALESCE(SUM(va.stock_fisico - va.stock_reservado), 0) AS disponibles"
                + " FROM variante va JOIN producto p ON p.id = va.producto_id"
                + " WHERE va.activo = TRUE AND p.activo = TRUE";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, n) -> new Existencias(
                rs.getLong("variantes"), rs.getBigDecimal("fisicas").longValue(),
                rs.getBigDecimal("disponibles").longValue()));
    }

    /**
     * Valor del inventario igual al de la valorización (REQ-07): todas las variantes con físico, activas o no. Así
     * el panel concilia con el reporte; las inactivas con stock siguen siendo plata en bodega.
     */
    public BigDecimal[] valorInventario() {
        String sql = "SELECT COALESCE(SUM(va.stock_fisico * p.precio_compra), 0) AS valor_costo,"
                + " COALESCE(SUM(va.stock_fisico * p.precio_venta), 0) AS valor_venta"
                + " FROM variante va JOIN producto p ON p.id = va.producto_id WHERE va.stock_fisico > 0";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, n) -> new BigDecimal[] {
            rs.getBigDecimal("valor_costo"), rs.getBigDecimal("valor_venta")});
    }

    public AlertasActivas alertasActivas() {
        String sql = "SELECT COALESCE(SUM(tipo = 'AGOTADO'), 0) AS agotado,"
                + " COALESCE(SUM(tipo = 'STOCK_BAJO'), 0) AS stock_bajo,"
                + " COALESCE(SUM(tipo = 'SIN_ROTACION'), 0) AS sin_rotacion FROM alerta WHERE estado = 'ACTIVA'";
        return jdbc.queryForObject(sql, new MapSqlParameterSource(), (rs, n) -> new AlertasActivas(
                rs.getLong("agotado"), rs.getLong("stock_bajo"), rs.getLong("sin_rotacion")));
    }

    /** Apartados ACTIVOS; {@code porVencer}: vencen a más tardar en {@code limite}. Solo conteos. */
    public ReservasActivas reservasActivas(LocalDateTime limite) {
        String sql = "SELECT COUNT(*) AS activas, COALESCE(SUM(cantidad), 0) AS unidades,"
                + " COALESCE(SUM(CASE WHEN vence_en <= :limite THEN 1 ELSE 0 END), 0) AS por_vencer"
                + " FROM reserva WHERE estado = 'ACTIVA'";
        return jdbc.queryForObject(sql, new MapSqlParameterSource("limite", limite), (rs, n) -> new ReservasActivas(
                rs.getLong("activas"), rs.getBigDecimal("unidades").longValue(),
                rs.getBigDecimal("por_vencer").longValue()));
    }

    /**
     * Planes de compra: BORRADOR, APROBADO (en camino) y atrasados = aprobados cuya llegada esperada
     * ({@code aprobado_en + max(lead_time_dias)} de sus productos) ya pasó.
     */
    public Compras compras(LocalDateTime ahora) {
        MapSqlParameterSource params = new MapSqlParameterSource("ahora", ahora);
        Long borradores = jdbc.queryForObject("SELECT COUNT(*) FROM plan_compra WHERE estado = 'BORRADOR'", params,
                Long.class);
        String sql = "SELECT COUNT(*) AS planes, COALESCE(SUM(t.unidades), 0) AS unidades,"
                + " COALESCE(SUM(t.valor), 0) AS valor,"
                + " COALESCE(SUM(CASE WHEN DATE_ADD(t.aprobado_en, INTERVAL t.dias_lead DAY) < :ahora THEN 1 ELSE 0 END), 0)"
                + " AS atrasados FROM (SELECT pc.id, pc.aprobado_en, SUM(dp.cantidad_aprobada) AS unidades,"
                + " SUM(dp.cantidad_aprobada * dp.costo_estimado) AS valor, MAX(p.lead_time_dias) AS dias_lead"
                + " FROM plan_compra pc JOIN detalle_plan dp ON dp.plan_id = pc.id"
                + " JOIN variante va ON va.id = dp.variante_id JOIN producto p ON p.id = va.producto_id"
                + " WHERE pc.estado = 'APROBADO' GROUP BY pc.id, pc.aprobado_en) t";
        return jdbc.queryForObject(sql, params, (rs, n) -> new Compras(borradores == null ? 0 : borradores,
                rs.getLong("planes"), rs.getBigDecimal("unidades").longValue(), rs.getBigDecimal("valor"),
                rs.getBigDecimal("atrasados").longValue()));
    }

    private static BigDecimal cero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
