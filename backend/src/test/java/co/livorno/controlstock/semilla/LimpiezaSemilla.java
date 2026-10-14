package co.livorno.controlstock.semilla;

import org.springframework.jdbc.core.JdbcTemplate;

/** Borra lo que genera la semilla (solo pruebas): el kardex no se borra desde el código de producción. */
final class LimpiezaSemilla {

    private LimpiezaSemilla() {
    }

    static void limpiar(JdbcTemplate jdbc) {
        for (String tabla : new String[] {"alerta", "analitica_variante", "ejecucion_analitica", "devolucion", "reserva",
                "detalle_venta", "movimiento", "venta", "variante", "producto"}) {
            jdbc.update("delete from " + tabla);
        }
        jdbc.update("delete from usuario where email like '%@livorno.co'");
    }
}
