package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.calculo.SaldoDisponible;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saldos de disponible desde el kardex para reconstruir los dias con stock (ADR-010, decision 1), solo lectura. Dos
 * consultas por lote (variantes y saldos), no una por variante. El disponible tras un movimiento es
 * {@code fisico_despues - reservado_despues}, de modo que una RESERVA que lo deja en 0 cuenta como sin stock.
 *
 * <p>Universo: variantes activas de productos activos (las inactivas no aportan a la disponibilidad). El indice
 * {@code idx_movimiento_variante_fecha} (variante, fecha, id) resuelve el ultimo saldo previo a la ventana.
 */
@Component
@Transactional(readOnly = true)
public class ConsultasDisponibilidad {

    private static final String VARIANTES = """
            SELECT va.id AS variante_id, va.producto_id AS producto_id, DATE(va.creado_en) AS creada_en
            FROM variante va
            JOIN producto p ON p.id = va.producto_id
            WHERE va.activo = TRUE AND p.activo = TRUE
            ORDER BY va.id
            """;

    /** Saldos en [desde, hasta) mas el ultimo anterior a desde (el que fija la apertura del primer dia). */
    private static final String SALDOS = """
            SELECT m.variante_id AS variante_id, DATE(m.fecha) AS dia,
                   m.fisico_despues - m.reservado_despues AS disponible
            FROM movimiento m
            JOIN variante va ON va.id = m.variante_id
            JOIN producto p ON p.id = va.producto_id
            WHERE va.activo = TRUE AND p.activo = TRUE AND m.fecha < :hasta
              AND (m.fecha >= :desde
                   OR m.id = (SELECT m2.id FROM movimiento m2
                              WHERE m2.variante_id = m.variante_id AND m2.fecha < :desde
                              ORDER BY m2.fecha DESC, m2.id DESC LIMIT 1))
            ORDER BY m.variante_id, m.fecha, m.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasDisponibilidad(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Historial de disponibilidad de cada variante activa para la ventana {@code [desde, hasta)}. Una sola llamada con
     * la ventana mas amplia (la serie de hasta 36 meses) sirve tambien para la de 180 dias: {@code CorrectorCensura}
     * usa los saldos anteriores al inicio solo para abrir el primer dia. Ordenado por id de variante.
     */
    public List<HistorialDisponibilidad> historial(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El rango debe cumplir desde < hasta.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("desde", desde.atStartOfDay()).addValue("hasta", hasta.atStartOfDay());
        Map<Long, List<SaldoDisponible>> saldos = new HashMap<>();
        jdbc.query(SALDOS, params, rs -> {
            saldos.computeIfAbsent(rs.getLong("variante_id"), k -> new ArrayList<>())
                    .add(new SaldoDisponible(rs.getObject("dia", LocalDate.class), rs.getInt("disponible")));
        });
        return jdbc.query(VARIANTES, (rs, n) -> new HistorialDisponibilidad(rs.getLong("variante_id"),
                rs.getLong("producto_id"), rs.getObject("creada_en", LocalDate.class),
                saldos.getOrDefault(rs.getLong("variante_id"), List.of())));
    }
}
