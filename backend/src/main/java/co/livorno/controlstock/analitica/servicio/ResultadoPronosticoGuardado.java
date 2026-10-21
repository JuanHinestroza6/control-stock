package co.livorno.controlstock.analitica.servicio;

import co.livorno.controlstock.analitica.calculo.PronosticoMensual;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Lo que B4 necesita del paso de pronóstico, en memoria: el pronóstico mensual de cada variante activa de un producto
 * pronosticado (fuente del ROP; las variantes de productos sin pronóstico no aparecen) y los agregados de
 * {@code ejecucion_analitica}.
 *
 * @param porVariante            varianteId → pronóstico del mes actual y los siguientes
 * @param productosPronosticados productos con pronóstico (SES o HW; excluye SIN_HISTORIA)
 * @param conHoltWinters         productos cuyo método elegido es HOLT_WINTERS
 * @param conSes                 productos cuyo método elegido es SES
 * @param wapeGlobal             Σ|error| / Σ real del backtest de todos los productos con backtest (escala 4); null si
 *                               ninguno tiene backtest o todo lo real es 0
 */
public record ResultadoPronosticoGuardado(Map<Long, PronosticoMensual> porVariante, int productosPronosticados,
        int conHoltWinters, int conSes, BigDecimal wapeGlobal) {

    public ResultadoPronosticoGuardado {
        porVariante = Map.copyOf(porVariante);
    }
}
