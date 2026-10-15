package co.livorno.controlstock.semilla;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import co.livorno.controlstock.inventario.servicio.AlertaService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verificación de solo lectura de los invariantes de la carga (ADR-008). Cada comprobación es una consulta que debe
 * devolver 0 filas; si alguna falla lanza {@link SemillaAbortadaException} con el detalle.
 */
@Component
@Profile("seed")
public class VerificadorConsistencia {

    private final JdbcTemplate jdbc;
    private final AlertaService alertas;

    public VerificadorConsistencia(JdbcTemplate jdbc, AlertaService alertas) {
        this.jdbc = jdbc;
        this.alertas = alertas;
    }

    /** Invariantes de stock, kardex, ventas, devoluciones, reservas y usuarios. Dentro de la transacción de la carga. */
    public void verificarCarga(LocalDate inicio, LocalDate fin) {
        Timestamp desde = Timestamp.valueOf(inicio.atStartOfDay());
        Timestamp hasta = Timestamp.valueOf(fin.plusDays(1).atStartOfDay().minusNanos(1000));
        Map<String, String> reglas = new LinkedHashMap<>();
        reglas.put("físico distinto de la suma del kardex", """
                select count(*) from variante v where v.stock_fisico <>
                (select coalesce(sum(m.fisico_despues - m.fisico_antes), 0) from movimiento m where m.variante_id = v.id)""");
        reglas.put("reservado distinto de la suma del kardex", """
                select count(*) from variante v where v.stock_reservado <>
                (select coalesce(sum(m.reservado_despues - m.reservado_antes), 0) from movimiento m
                 where m.variante_id = v.id)""");
        reglas.put("reservado distinto de las reservas ACTIVAS", """
                select count(*) from variante v where v.stock_reservado <>
                (select coalesce(sum(r.cantidad), 0) from reserva r where r.variante_id = v.id
                 and r.estado = 'ACTIVA')""");
        reglas.put("stock negativo", """
                select count(*) from variante where stock_fisico < 0 or stock_reservado < 0
                or stock_fisico - stock_reservado < 0""");
        reglas.put("kardex con saldos negativos", """
                select count(*) from movimiento where fisico_despues < 0 or reservado_despues < 0
                or fisico_despues < reservado_despues""");
        reglas.put("kardex no encadenado (físico)", """
                select count(*) from (select fisico_antes, lag(fisico_despues) over (partition by variante_id
                order by id) as previo from movimiento) t where previo is not null and fisico_antes <> previo""");
        reglas.put("kardex no encadenado (reservado)", """
                select count(*) from (select reservado_antes, lag(reservado_despues) over (partition by variante_id
                order by id) as previo from movimiento) t where previo is not null and reservado_antes <> previo""");
        reglas.put("fechas del kardex decrecientes", """
                select count(*) from (select fecha, lag(fecha) over (order by id) as previa from movimiento) t
                where previa is not null and fecha < previa""");
        reglas.put("total de venta distinto de la suma de líneas", """
                select count(*) from venta v where v.total <>
                (select coalesce(sum(d.cantidad * d.precio_unitario), 0) from detalle_venta d where d.venta_id = v.id)""");
        reglas.put("devuelto mayor que vendido", """
                select count(*) from detalle_venta d where d.cantidad <
                (select coalesce(sum(dv.cantidad), 0) from devolucion dv where dv.detalle_venta_id = d.id)""");
        reglas.put("devoluciones en ventas anuladas", """
                select count(*) from devolucion dv join detalle_venta d on d.id = dv.detalle_venta_id
                join venta v on v.id = d.venta_id where v.estado = 'ANULADA'""");
        reglas.put("venta anulada sin ANULACION_VENTA por línea", """
                select count(*) from venta v where v.estado = 'ANULADA' and
                (select count(*) from movimiento m where m.tipo = 'ANULACION_VENTA' and m.referencia_tipo = 'VENTA'
                 and m.referencia_id = v.id) <> (select count(*) from detalle_venta d where d.venta_id = v.id)""");
        reglas.put("reserva CONVERTIDA sin venta o VENCIDA con usuario", """
                select count(*) from reserva where (estado = 'CONVERTIDA' and venta_id is null)
                or (estado = 'VENCIDA' and cerrada_por is not null) or (estado = 'ACTIVA' and cerrada_en is not null)
                or (estado <> 'ACTIVA' and cerrada_en is null)""");
        reglas.put("usuarios demo con rol distinto, inactivos o con cambio forzado", """
                select case when count(*) = 4 and sum(case
                  when (email = 'admin@livorno.co' and rol = 'ADMIN')
                    or (email = 'inventario@livorno.co' and rol = 'INVENTARIO')
                    or (email = 'ventas@livorno.co' and rol = 'VENDEDOR')
                    or (email = 'contabilidad@livorno.co' and rol = 'CONTABILIDAD') then 0 else 1 end) = 0
                  and sum(debe_cambiar_password) = 0 and sum(case when activo = 1 then 0 else 1 end) = 0
                  then 0 else 1 end
                from usuario where email in ('admin@livorno.co', 'inventario@livorno.co', 'ventas@livorno.co',
                'contabilidad@livorno.co')""");

        List<String> fallos = new ArrayList<>();
        reglas.forEach((nombre, sql) -> {
            Long filas = jdbc.queryForObject(sql, Long.class);
            if (filas != null && filas > 0) {
                fallos.add(nombre + " (" + filas + ")");
            }
        });
        for (String tabla : List.of("movimiento:fecha", "venta:fecha", "devolucion:fecha", "reserva:creada_en")) {
            String[] partes = tabla.split(":");
            Long fuera = jdbc.queryForObject("select count(*) from " + partes[0] + " where " + partes[1]
                    + " < ? or " + partes[1] + " > ?", Long.class, desde, hasta);
            if (fuera != null && fuera > 0) {
                fallos.add(partes[0] + " con fechas fuera del rango (" + fuera + ")");
            }
        }
        if (!fallos.isEmpty()) {
            throw new SemillaAbortadaException("Verificación de consistencia fallida: " + String.join("; ", fallos));
        }
    }

    /**
     * Tras evaluar las alertas: para cada variante activa la alerta ACTIVA coincide con {@code tipoEsperado} (AGOTADO,
     * SIN_ROTACION, STOCK_BAJO o ninguna) y no hay alertas ACTIVAS de variantes inactivas.
     */
    public void verificarAlertas() {
        Map<Long, String> activas = new java.util.HashMap<>();
        jdbc.query("select variante_id, tipo from alerta where estado = 'ACTIVA'",
                rs -> { activas.put(rs.getLong(1), rs.getString(2)); });
        long incoherentes = 0;
        for (Long id : jdbc.queryForList("select id from variante where activo = 1 order by id", Long.class)) {
            var esperado = alertas.tipoEsperado(id);
            if (!java.util.Objects.equals(activas.get(id), esperado == null ? null : esperado.name())) {
                incoherentes++;
            }
        }
        Long deInactivas = jdbc.queryForObject("""
                select count(*) from alerta a join variante v on v.id = a.variante_id
                where a.estado = 'ACTIVA' and v.activo = 0""", Long.class);
        if (incoherentes > 0 || (deInactivas != null && deInactivas > 0)) {
            throw new SemillaAbortadaException("Alertas incoherentes con el stock: " + incoherentes
                    + " variantes activas, " + deInactivas + " alertas de variantes inactivas.");
        }
    }
}
