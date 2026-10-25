package co.livorno.controlstock.semilla;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Resultado de la carga. {@code generado=false}: la base ya tenía datos y no se hizo nada. Las alertas se cuentan
 * tras evaluarlas con el reloj real (después de confirmar la carga).
 */
public record ResumenSemilla(boolean generado, LocalDate inicio, LocalDate fin, int usuarios, int productos,
                             int variantes, long unidadesIniciales, ResultadoSimulacion simulacion,
                             long reservasActivas, long alertasAgotado, long alertasStockBajo, long duracionMs,
                             CierreAnalitica analitica) {

    /**
     * Resultado de la analítica de cierre (origen SEMILLA): clases, variantes con ROP y su método, métrica de alertas
     * ({@code porcentajeAlerta}: AGOTADO o STOCK_BAJO sobre variantes activas) y SIN_ROTACION aparte. Desde la fase 6:
     * productos por método de pronóstico, WAPE global, y las variantes en alerta sin contar lo en camino (contrafactual)
     * y las que tienen pedido en camino. Los campos de pronóstico son null si el pronóstico no corrió. Desde la fase 7:
     * {@code fechaCorte} de la ejecución y {@code unidadesAgregadasCorreccion} = Σ (corregidas − observadas) de los 12
     * meses completos antes del mes del corte en analitica_producto,
     * lo que la corrección por agotados suma a la demanda observada.
     */
    public record CierreAnalitica(int claseA, int claseB, int claseC, int conPuntoReorden,
                                  Map<String, Long> ropPorMetodo, int variantesActivas, int enAlertaStock,
                                  BigDecimal porcentajeAlerta, long alertasSinRotacion, long duracionMs,
                                  Integer productosHoltWinters, Integer productosSes, BigDecimal wapeGlobal,
                                  Integer enAlertaSinTransito, Integer conPedidoEnCamino, LocalDate fechaCorte,
                                  BigDecimal unidadesAgregadasCorreccion) {

        /** % de variantes activas en alerta de stock si no se contara lo que viene en camino. */
        public BigDecimal porcentajeSinTransito() {
            if (enAlertaSinTransito == null || variantesActivas == 0) {
                return null;
            }
            return BigDecimal.valueOf(enAlertaSinTransito * 100L).divide(BigDecimal.valueOf(variantesActivas), 1,
                    RoundingMode.HALF_UP);
        }
    }

    /**
     * Unidades perdidas evitables en la ventana de la corrección por agotados: los 12 meses completos antes del mes de la
     * fecha de corte de la analítica de cierre (la misma serie que corrige). Null sin analítica.
     */
    public Long unidadesPerdidasComparables() {
        if (analitica == null || analitica.fechaCorte() == null || simulacion == null) {
            return null;
        }
        return simulacion.unidadesPerdidasComparables(YearMonth.from(analitica.fechaCorte()));
    }

    /**
     * Cuántas veces cubre la corrección por agotados las unidades perdidas comparables (se espera 0,5×–2×); null si no
     * hubo pérdidas comparables o no hay analítica. La corrección también ve las variantes que se dejan agotar a
     * propósito (siguen activas), que el denominador excluye: la razón tiende a quedar algo alta por eso.
     */
    public BigDecimal razonCorreccion() {
        Long comparables = unidadesPerdidasComparables();
        if (comparables == null || comparables == 0 || analitica.unidadesAgregadasCorreccion() == null) {
            return null;
        }
        return analitica.unidadesAgregadasCorreccion()
                .divide(BigDecimal.valueOf(comparables), 2, RoundingMode.HALF_UP);
    }

    public static ResumenSemilla yaHabiaDatos() {
        return new ResumenSemilla(false, null, null, 0, 0, 0, 0, null, 0, 0, 0, 0, null);
    }

    /** Copia con los datos del cierre (alertas, reservas activas y duración). */
    public ResumenSemilla conCierre(long reservasActivas, long alertasAgotado, long alertasStockBajo,
                                    long duracionMs, CierreAnalitica analitica) {
        return new ResumenSemilla(generado, inicio, fin, usuarios, productos, variantes, unidadesIniciales,
                simulacion, reservasActivas, alertasAgotado, alertasStockBajo, duracionMs, analitica);
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
                  planes de compra: %d creados, %d recibidos, %d en camino (%d unidades)
                  reservas: %d (convertidas %d, canceladas %d, vencidas %d, activas %d)
                  alertas activas: AGOTADO %d, STOCK_BAJO %d, SIN_ROTACION %d""",
                inicio, fin, duracionMs / 1000.0, usuarios, productos, variantes, unidadesIniciales, s.ventas(),
                s.anuladas(), s.unidadesVendidas(), s.ingreso().toPlainString(), s.lineasPerdidas(),
                s.porcentajePerdidasEvitables(), s.unidadesPerdidas(), s.devoluciones(), s.devolucionesNoAptas(),
                s.entradas(), s.ajustes(), s.alzasDePrecio(), s.productosDesactivados(), s.planesCreados(),
                s.planesRecibidos(), s.planesEnCamino(), s.unidadesEnCamino(), s.reservasCreadas(),
                s.reservasConvertidas(), s.reservasCanceladas(), s.reservasVencidas(), reservasActivas,
                alertasAgotado, alertasStockBajo, analitica == null ? 0 : analitica.alertasSinRotacion())
                + textoAnalitica();
    }

    private String textoAnalitica() {
        if (analitica == null) {
            return "";
        }
        return String.format("""
                
  analítica de cierre (%.1f s): clases A %d, B %d, C %d | variantes con ROP: %d %s
                  en alerta de stock: %d de %d variantes activas (%s %%) | SIN_ROTACION aparte: %d
                  pronóstico: Holt-Winters %s, SES %s, WAPE global %s | en alerta sin contar lo en camino: %s (%s %%)
                  variantes con pedido en camino: %s
                  corrección por agotados (12 meses antes de %s): +%s unidades frente a %s perdidas comparables
                  = %s× (esperado 0,5×–2×)""",
                analitica.duracionMs() / 1000.0, analitica.claseA(), analitica.claseB(), analitica.claseC(),
                analitica.conPuntoReorden(), analitica.ropPorMetodo(), analitica.enAlertaStock(),
                analitica.variantesActivas(),
                analitica.porcentajeAlerta() == null ? "n/d" : analitica.porcentajeAlerta().toPlainString(),
                analitica.alertasSinRotacion(), nd(analitica.productosHoltWinters()), nd(analitica.productosSes()),
                nd(analitica.wapeGlobal() == null ? null : analitica.wapeGlobal().toPlainString()),
                nd(analitica.enAlertaSinTransito()),
                nd(analitica.porcentajeSinTransito() == null ? null : analitica.porcentajeSinTransito().toPlainString()),
                nd(analitica.conPedidoEnCamino()),
                nd(analitica.fechaCorte() == null ? null : YearMonth.from(analitica.fechaCorte())),
                nd(analitica.unidadesAgregadasCorreccion() == null ? null
                        : analitica.unidadesAgregadasCorreccion().toPlainString()),
                nd(unidadesPerdidasComparables()),
                nd(razonCorreccion() == null ? null : razonCorreccion().toPlainString()));
    }

    private static String nd(Object valor) {
        return valor == null ? "n/d" : valor.toString();
    }
}
