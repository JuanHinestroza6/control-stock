package co.livorno.controlstock.semilla;

import co.livorno.controlstock.comun.config.ConfiguracionTiempo;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Borra lo que genera la semilla (solo pruebas): el kardex no se borra desde el código de producción. */
final class LimpiezaSemilla {

    private LimpiezaSemilla() {
    }

    /**
     * Fija el "reloj real" de la semilla en {@code fechaFin + 1} (03:00, America/Bogota): la analítica de cierre usa
     * como corte el día siguiente al fin de la historia, como en producción con fecha-fin = ayer.
     */
    static void fijarCierreTrasFin(RelojSimulado reloj, LocalDate fechaFin) {
        reloj.sustituirRelojDelSistema(Clock.fixed(
                fechaFin.plusDays(1).atTime(3, 0).atZone(ConfiguracionTiempo.ZONA).toInstant(),
                ConfiguracionTiempo.ZONA));
    }

    static void restablecerReloj(RelojSimulado reloj) {
        reloj.sustituirRelojDelSistema(null);
    }

    static void limpiar(JdbcTemplate jdbc) {
        for (String tabla : new String[] {"alerta", "pronostico", "analitica_producto_mes", "analitica_producto",
            "analitica_variante", "ejecucion_analitica", "devolucion", "reserva",
                "detalle_venta", "movimiento", "venta", "variante", "producto"}) {
            jdbc.update("delete from " + tabla);
        }
        jdbc.update("delete from usuario where email like '%@livorno.co'");
    }
}
