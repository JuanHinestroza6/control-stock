package co.livorno.controlstock.semilla;

import java.time.LocalDate;

/**
 * Resultado de la carga. {@code generado=false}: la base ya tenía datos y no se hizo nada. Las alertas se cuentan
 * tras evaluarlas con el reloj real (después de confirmar la carga).
 */
public record ResumenSemilla(boolean generado, LocalDate inicio, LocalDate fin, int usuarios, int productos,
                             int variantes, long unidadesIniciales, ResultadoSimulacion simulacion,
                             long reservasActivas, long alertasAgotado, long alertasStockBajo, long duracionMs) {

    public static ResumenSemilla yaHabiaDatos() {
        return new ResumenSemilla(false, null, null, 0, 0, 0, 0, null, 0, 0, 0, 0);
    }

    /** Copia con los datos del cierre (alertas, reservas activas y duración). */
    public ResumenSemilla conCierre(long reservasActivas, long alertasAgotado, long alertasStockBajo,
                                    long duracionMs) {
        return new ResumenSemilla(generado, inicio, fin, usuarios, productos, variantes, unidadesIniciales,
                simulacion, reservasActivas, alertasAgotado, alertasStockBajo, duracionMs);
    }

    /** Texto multilínea para el log. */
    public String texto() {
        if (!generado) {
            return "Datos semilla: la base ya tenía ventas; no se hizo nada.";
        }
        ResultadoSimulacion s = simulacion;
        return String.format("""
                Datos semilla generados (%s a %s) en %.1f s
                  usuarios demo: %d | productos: %d | variantes: %d | unidades iniciales: %d
                  ventas: %d (anuladas %d) | unidades vendidas: %d | ingreso: %s
                  ventas perdidas: %d líneas (%.1f %% evitables), %d unidades
                  devoluciones: %d (no aptas %d) | entradas: %d | ajustes: %d | alzas de precio: %d | desactivados: %d
                  reservas: %d (convertidas %d, canceladas %d, vencidas %d, activas %d)
                  alertas activas: AGOTADO %d, STOCK_BAJO %d""",
                inicio, fin, duracionMs / 1000.0, usuarios, productos, variantes, unidadesIniciales, s.ventas(),
                s.anuladas(), s.unidadesVendidas(), s.ingreso().toPlainString(), s.lineasPerdidas(),
                s.porcentajePerdidasEvitables(), s.unidadesPerdidas(), s.devoluciones(), s.devolucionesNoAptas(),
                s.entradas(), s.ajustes(), s.alzasDePrecio(), s.productosDesactivados(), s.reservasCreadas(),
                s.reservasConvertidas(), s.reservasCanceladas(), s.reservasVencidas(), reservasActivas,
                alertasAgotado, alertasStockBajo);
    }
}
