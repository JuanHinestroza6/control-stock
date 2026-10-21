package co.livorno.controlstock.analitica.repositorio;

import co.livorno.controlstock.analitica.dto.PuntoPronostico;
import co.livorno.controlstock.analitica.dto.PuntoSerie;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lectura del último pronóstico (tablas analitica_producto, analitica_producto_mes y pronostico). */
@Component
@Transactional(readOnly = true)
public class ConsultasPronostico {

    private static final String PRODUCTO = """
            SELECT p.id AS producto_id, p.codigo, p.nombre, c.nombre AS categoria, ap.metodo, ap.alfa, ap.beta, ap.gamma,
                   ap.wape_ses, ap.wape_hw, ap.error_wape, ap.motivo_sin_hw, ap.meses_historia,
                   ap.unidades_observadas_12m, ap.unidades_corregidas_12m, ap.calculado_en
            FROM analitica_producto ap
            JOIN producto p ON p.id = ap.producto_id
            JOIN categoria c ON c.id = p.categoria_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasPronostico(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static FilaPronosticoProducto producto(ResultSet rs, int n) throws SQLException {
        return new FilaPronosticoProducto(rs.getLong("producto_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("categoria"), rs.getString("metodo"), rs.getBigDecimal("alfa"), rs.getBigDecimal("beta"),
                rs.getBigDecimal("gamma"), rs.getBigDecimal("wape_ses"), rs.getBigDecimal("wape_hw"),
                rs.getBigDecimal("error_wape"), rs.getString("motivo_sin_hw"), rs.getInt("meses_historia"),
                rs.getInt("unidades_observadas_12m"), rs.getBigDecimal("unidades_corregidas_12m"),
                rs.getObject("calculado_en", LocalDateTime.class));
    }

    /**
     * Productos activos con instantánea. {@code orden} ya viene traducido a columnas desde una lista blanca (en el
     * servicio); {@code patron} se construye con {@code ProductoRepository.patronContiene}.
     */
    public List<FilaPronosticoProducto> pagina(Long categoriaId, String patron, String metodo, String orden,
            int limite, long desplazamiento) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = PRODUCTO + filtros(categoriaId, patron, metodo, params) + " ORDER BY " + orden
                + " LIMIT :limite OFFSET :offset";
        params.addValue("limite", limite).addValue("offset", desplazamiento);
        return jdbc.query(sql, params, ConsultasPronostico::producto);
    }

    public long contar(Long categoriaId, String patron, String metodo) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) FROM analitica_producto ap JOIN producto p ON p.id = ap.producto_id "
                + "JOIN categoria c ON c.id = p.categoria_id" + filtros(categoriaId, patron, metodo, params);
        Long total = jdbc.queryForObject(sql, params, Long.class);
        return total != null ? total : 0L;
    }

    private static String filtros(Long categoriaId, String patron, String metodo, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder(" WHERE p.activo = TRUE");
        if (categoriaId != null) {
            where.append(" AND p.categoria_id = :categoriaId");
            params.addValue("categoriaId", categoriaId);
        }
        if (patron != null) {
            where.append(" AND (p.codigo LIKE :patron ESCAPE '!' OR p.nombre LIKE :patron ESCAPE '!')");
            params.addValue("patron", patron);
        }
        if (metodo != null) {
            where.append(" AND ap.metodo = :metodo");
            params.addValue("metodo", metodo);
        }
        return where.toString();
    }

    public Optional<FilaPronosticoProducto> producto(long productoId) {
        return jdbc.query(PRODUCTO + " WHERE ap.producto_id = :id", Map.of("id", productoId),
                ConsultasPronostico::producto).stream().findFirst();
    }

    /** Pronóstico del producto (total, sin variante) por periodo ascendente, para varios productos. */
    public Map<Long, List<PuntoPronostico>> pronosticoProductos(Collection<Long> productoIds) {
        Map<Long, List<PuntoPronostico>> resultado = new HashMap<>();
        if (productoIds.isEmpty()) {
            return resultado;
        }
        jdbc.query("SELECT producto_id, periodo, cantidad_proyectada FROM pronostico"
                + " WHERE variante_id IS NULL AND producto_id IN (:ids) ORDER BY producto_id, periodo",
                Map.of("ids", productoIds), rs -> {
                    resultado.computeIfAbsent(rs.getLong("producto_id"), k -> new ArrayList<>()).add(
                            new PuntoPronostico(YearMonth.from(rs.getDate("periodo").toLocalDate()).toString(),
                                    rs.getBigDecimal("cantidad_proyectada")));
                });
        return resultado;
    }

    public List<PuntoSerie> serie(long productoId) {
        return jdbc.query("SELECT periodo, unidades_observadas, disponibilidad, unidades_corregidas, backtest_ses,"
                + " backtest_hw FROM analitica_producto_mes WHERE producto_id = :id ORDER BY periodo",
                Map.of("id", productoId), (rs, n) -> new PuntoSerie(
                        YearMonth.from(rs.getDate("periodo").toLocalDate()).toString(),
                        rs.getInt("unidades_observadas"), rs.getBigDecimal("disponibilidad"),
                        rs.getBigDecimal("unidades_corregidas"), rs.getBigDecimal("backtest_ses"),
                        rs.getBigDecimal("backtest_hw")));
    }

    /** Pronóstico por variante del producto, por variante y periodo ascendentes. */
    public List<FilaPronosticoVariante> variantes(long productoId) {
        return jdbc.query("SELECT v.id AS variante_id, v.sku, v.talla, v.color, f.periodo, f.cantidad_proyectada"
                + " FROM pronostico f JOIN variante v ON v.id = f.variante_id"
                + " WHERE f.producto_id = :id ORDER BY v.id, f.periodo", Map.of("id", productoId),
                (rs, n) -> new FilaPronosticoVariante(rs.getLong("variante_id"), rs.getString("sku"),
                        rs.getString("talla"), rs.getString("color"),
                        YearMonth.from(rs.getDate("periodo").toLocalDate()), rs.getBigDecimal("cantidad_proyectada")));
    }
}
